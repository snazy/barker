/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.caffinitas.barker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;

import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

public class BarksDAO
{
    public static final long SLICE_MILLIS = TimeUnit.HOURS.toMillis(1);
    public static final int MAX_SLICE_READS = 100;

    private final Session session;

    private final PreparedStatement pstmtReadFollowing;
    private final PreparedStatement pstmtWriteTimeline;
    private final PreparedStatement pstmtWriteBarks;
    private final PreparedStatement pstmtReadBarks;
    private final PreparedStatement pstmtReadTimeline;
    private final PreparedStatement pstmtFollowerAdd;
    private final PreparedStatement pstmtFollowerRemove;
    private final PreparedStatement pstmtFollowingAdd;
    private final PreparedStatement pstmtFollowingRemove;

    private final Histogram histReads = new Histogram(new ExponentiallyDecayingReservoir());
    private final Histogram histWrites = new Histogram(new ExponentiallyDecayingReservoir());
    private final Meter successes = new Meter();
    private final Meter errors = new Meter();

    public BarksDAO(Session session) throws ExecutionException
    {
        this.session = session;

        ListenableFuture<PreparedStatement> fReadFollowing = session.prepareAsync("SELECT followers FROM barker.users WHERE username = ?");
        ListenableFuture<PreparedStatement> fWriteTimeline = session.prepareAsync("INSERT INTO barker.timeline (username, slice, ts, sender, message) VALUES (?, ?, ?, ?, ?)");
        ListenableFuture<PreparedStatement> fWriteBarks = session.prepareAsync("INSERT INTO barker.barks (username, slice, ts, message) VALUES (?, ?, ?, ?)");
        ListenableFuture<PreparedStatement> fReadBarks = session.prepareAsync("SELECT ts, message FROM barker.barks WHERE username = ? AND slice = ?");
        ListenableFuture<PreparedStatement> fReadTimeline = session.prepareAsync("SELECT sender, ts, message FROM barker.timeline WHERE username = ? AND slice = ?");
        ListenableFuture<PreparedStatement> fFollowerAdd = session.prepareAsync("UPDATE barker.users SET followers = followers + ? WHERE username = ?");
        ListenableFuture<PreparedStatement> fFollowingAdd = session.prepareAsync("UPDATE barker.users SET following = following + ? WHERE username = ?");
        ListenableFuture<PreparedStatement> fFollowerRemove = session.prepareAsync("UPDATE barker.users SET followers = followers - ? WHERE username = ?");
        ListenableFuture<PreparedStatement> fFollowingRemove = session.prepareAsync("UPDATE barker.users SET following = following - ? WHERE username = ?");

        pstmtReadFollowing = Uninterruptibles.getUninterruptibly(fReadFollowing);
        pstmtWriteTimeline = Uninterruptibles.getUninterruptibly(fWriteTimeline);
        pstmtWriteBarks = Uninterruptibles.getUninterruptibly(fWriteBarks);
        pstmtReadBarks = Uninterruptibles.getUninterruptibly(fReadBarks);
        pstmtReadTimeline = Uninterruptibles.getUninterruptibly(fReadTimeline);
        pstmtFollowerAdd = Uninterruptibles.getUninterruptibly(fFollowerAdd);
        pstmtFollowerRemove = Uninterruptibles.getUninterruptibly(fFollowingAdd);
        pstmtFollowingAdd = Uninterruptibles.getUninterruptibly(fFollowerRemove);
        pstmtFollowingRemove = Uninterruptibles.getUninterruptibly(fFollowingRemove);
    }

    public Meter getErrors()
    {
        return errors;
    }

    public Meter getSuccesses()
    {
        return successes;
    }

    public Histogram getHistWrites()
    {
        return histWrites;
    }

    public Histogram getHistReads()
    {
        return histReads;
    }

    private long slice(long timeMillis)
    {
        return timeMillis - timeMillis % SLICE_MILLIS;
    }

    public void follow(String username, String following)
    {
        TimedFuture future1 = new TimedFuture(histWrites, pstmtFollowingAdd, Collections.singleton(following), username);
        TimedFuture future2 = new TimedFuture(histWrites, pstmtFollowerAdd, Collections.singleton(username), following);
        future1.getUninterruptibly();
        future2.getUninterruptibly();
    }

    public void unfollow(String username, String unfollowing)
    {
        TimedFuture future1 = new TimedFuture(histWrites, pstmtFollowingRemove, Collections.singleton(unfollowing), username);
        TimedFuture future2 = new TimedFuture(histWrites, pstmtFollowerRemove, Collections.singleton(username), unfollowing);
        future1.getUninterruptibly();
        future2.getUninterruptibly();
    }

    public void writeBark(String sender, String text)
    {
        Row row = new TimedFuture(histReads, pstmtReadFollowing, sender).getUninterruptibly().one();
        Set<String> followers = row.getSet(0, String.class);

        Date ts = new Date();
        Date slice = new Date(slice(ts.getTime()));

        List<TimedFuture> futures = new ArrayList<>(1 + followers.size());

        futures.add(new TimedFuture(histWrites, pstmtWriteBarks, sender, slice, ts, text));

        for (String follower : followers)
            futures.add(new TimedFuture(histWrites, pstmtWriteTimeline, follower, slice, ts, sender, text));

        for (TimedFuture future : futures)
            future.getUninterruptibly();
    }

    public List<Bark> readOwnBarks(String sender, int max)
    {
        List<Bark> barks = new ArrayList<>(max);

        long slice = slice(System.currentTimeMillis());
        for (int i = 0; i < MAX_SLICE_READS && barks.size() < max; i++, slice -= SLICE_MILLIS)
        {
            ResultSet rset = new TimedFuture(histReads, pstmtReadBarks, sender, new Date(slice)).getUninterruptibly();
            for (Row row : rset)
            {
                barks.add(new Bark(sender, row.getDate(0), row.getString(1)));
                if (barks.size() >= max)
                    break;
            }
        }

        return barks;
    }

    public List<Bark> readTimeline(String username, int max)
    {
        List<Bark> barks = new ArrayList<>(max);

        long slice = slice(System.currentTimeMillis());
        for (int i = 0; i < MAX_SLICE_READS && barks.size() < max; i++, slice -= SLICE_MILLIS)
        {
            ResultSet rset = new TimedFuture(histReads, pstmtReadTimeline, username, new Date(slice)).getUninterruptibly();
            for (Row row : rset)
            {
                barks.add(new Bark(row.getString(0), row.getDate(1), row.getString(2)));
                if (barks.size() >= max)
                    break;
            }
        }

        return barks;
    }

    class TimedFuture implements FutureCallback<ResultSet>
    {
        final long startTimestampNanos;
        final ResultSetFuture future;
        final Histogram histogram;

        TimedFuture(Histogram histogram, PreparedStatement pstmt, Object... objects)
        {
            this.histogram = histogram;
            this.startTimestampNanos = System.nanoTime();
            this.future = session.executeAsync(pstmt.bind(objects));
            Futures.addCallback(this.future, this);
        }

        public void onSuccess(ResultSet result)
        {
            this.histogram.update(TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startTimestampNanos));
            successes.mark();
        }

        public void onFailure(Throwable t)
        {
            this.histogram.update(TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startTimestampNanos));
            errors.mark();
        }

        public ResultSet getUninterruptibly()
        {
            return future.getUninterruptibly();
        }
    }
}
