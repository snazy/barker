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

import java.util.concurrent.ThreadLocalRandom;

public class Bot implements Runnable
{
    private final BotController botController;
    final String username;

    public Bot(BotController botController, String username)
    {
        this.botController = botController;
        this.username = username;
    }

    public void timeline(BarksDAO dao)
    {
        dao.readTimeline(username, 250);
    }

    public void ownBarks(BarksDAO dao)
    {
        dao.readOwnBarks(username, 250);
    }

    public void bark(BarksDAO dao)
    {
        int wordCount = 5 + ThreadLocalRandom.current().nextInt(20);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wordCount; i++)
        {
            if (i > 0)
                sb.append(' ');
            sb.append(Words.getRandomWord());
        }
        String text = sb.toString();

        dao.writeBark(username, text);
    }

    public void schedule()
    {
        botController.schedule(this);
    }

    public void run()
    {
        botController.runBot(this);
        botController.schedule(this);
    }
}
