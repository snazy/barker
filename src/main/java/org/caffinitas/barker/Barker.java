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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Snapshot;
import com.datastax.driver.core.Cluster;
import org.eclipse.jetty.server.Server;

public class Barker implements AutoCloseable
{
    private final Cluster cluster;

    public Barker(List<String> contactPoints)
    {
        Cluster.Builder clusterBuilder = Cluster.builder();
        for (String contactPoint : contactPoints)
        {
            clusterBuilder.addContactPoint(contactPoint);
        }

        this.cluster = clusterBuilder.build();
    }

    public void close()
    {
        cluster.close();
    }

    public static void main(String[] args)
    {
        List<String> contactPoints = new ArrayList<>();
        int threads = 16;
        int botCount = 50;
        long botDelayMin = 100;
        long botDelayMax = 2500;

        Iterator<String> argIter = Arrays.asList(args).iterator();
        while (argIter.hasNext())
        {
            String arg = argIter.next();

            if ("-c".equals(arg))
                contactPoints.add(argIter.next());
            else if ("-t".equals(arg))
                threads = Integer.parseInt(argIter.next());
            else if ("-b".equals(arg))
                botCount = Integer.parseInt(argIter.next());
            else if ("-min".equals(arg))
                botDelayMin = Long.parseLong(argIter.next());
            else if ("-max".equals(arg))
                botDelayMax = Long.parseLong(argIter.next());
        }

        if (contactPoints.isEmpty())
        {
            contactPoints.add("127.0.0.1");
            contactPoints.add("127.0.0.2");
        }

        Server server = new Server(new InetSocketAddress("127.0.0.1", 0));

        try
        {
            try (Barker barker = new Barker(contactPoints))
            {
                server.start();

                BarksDAO dao = new BarksDAO(barker.cluster.newSession());
                try (BotController ignored = new BotController(dao, threads, botCount, botDelayMin, botDelayMax))
                {
                    while (true)
                    {
                        System.console().printf("HTTP listener active on%n    %s%n" +
                                                "Enter EXIT to terminate this demo.%n" +
                                                "Enter STATS to view statistics.%n",
                                                server.getURI());

                        printStats(dao);

                        String line = System.console().readLine().trim().toLowerCase();
                        if ("exit".equals(line))
                        {
                            printStats(dao);
                            break;
                        }
                    }
                }
            }
            finally
            {
                server.stop();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private static void printStats(BarksDAO dao)
    {
        System.out.printf("%nStatistics %s%n" +
                          "                 count min[\u00b5s]   mean[\u00b5s] median[\u00b5s]    max[\u00b5s] stddev[\u00b5s]   " +
                          "75th[\u00b5s]   95th[\u00b5s]   98th[\u00b5s]   99th[\u00b5s]  999th[\u00b5s]%n",
                          new Date());
        printHistgram("reads", dao.getHistReads());
        printHistgram("writes", dao.getHistWrites());
        printMeter("ok", dao.getSuccesses());
        printMeter("errors", dao.getErrors());
        System.out.println();
    }

    private static void printMeter(String name, Meter meter)
    {
        System.out.printf("%-10s   count:%15d    1-minute-rate: %10.2f    5-minute-rate: %10.2f    15-minute-rate: %10.2f%n",
                          name,
                          meter.getCount(),
                          meter.getOneMinuteRate(),
                          meter.getFiveMinuteRate(),
                          meter.getFifteenMinuteRate());
    }

    private static void printHistgram(String name, Histogram hist)
    {
        Snapshot snapshot = hist.getSnapshot();
        System.out.printf("%-10s  %10d %7d %10.2f %10.2f %10d %10.2f " +
                          "%10.2f %10.2f %10.2f %10.2f %10.2f%n",
                          name,
                          hist.getCount(),
                          snapshot.getMin(),
                          snapshot.getMean(),
                          snapshot.getMedian(),
                          snapshot.getMax(),
                          snapshot.getStdDev(),
                          snapshot.get75thPercentile(),
                          snapshot.get95thPercentile(),
                          snapshot.get98thPercentile(),
                          snapshot.get99thPercentile(),
                          snapshot.get999thPercentile());
    }
}
