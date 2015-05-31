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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class BotController implements AutoCloseable
{
    private final int threads;
    private final int botCount;
    private final long botDelayMin;
    private final long botDelayMax;

    private final ScheduledExecutorService scheduler;

    private final BarksDAO dao;

    public BotController(BarksDAO dao, int threads, int botCount, long botDelayMin, long botDelayMax)
    {
        this.dao = dao;

        this.threads = threads;
        this.botCount = botCount;
        this.botDelayMin = botDelayMin;
        this.botDelayMax = botDelayMax;

        scheduler = Executors.newScheduledThreadPool(threads);

        for (int i = 1; i <= botCount; i++)
        {
            Bot bot = new Bot(this, String.format("bot_%07d", i));

            int j = i;
            for (int f = 0; f < 10; f++)
            {
                j++;
                if (j > botCount)
                    j = 1;

                String follows = String.format("bot_%07d", j);
                dao.follow(bot.username, follows);
            }

            bot.schedule();
        }
    }

    public void close()
    {
        scheduler.shutdown();
    }

    public void schedule(Bot bot)
    {
        long delay = ThreadLocalRandom.current().nextLong(botDelayMax - botDelayMin) + botDelayMin;
        scheduler.schedule(bot, delay, TimeUnit.MILLISECONDS);
    }

    public void runBot(Bot bot)
    {
        bot.timeline(dao);
        bot.bark(dao);
    }
}
