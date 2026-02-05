/*
 * This file is part of Dependency-Track Jenkins plugin.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jenkinsci.plugins.DependencyTrack;

import hudson.console.LineTransformationOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import org.jenkinsci.plugins.DependencyTrack.api.Logger;

class ConsoleLogger extends LineTransformationOutputStream implements Logger {

    private static final String PREFIX = "[DependencyTrack] ";
    private final PrintStream logger;

    protected ConsoleLogger(PrintStream logger) {
        this.logger = logger;
    }

    ConsoleLogger() {
        this(System.err);
    }


    @Override
    public void log(final String message) {
        logger.println(PREFIX + message.replace(System.lineSeparator(), System.lineSeparator() + PREFIX));
    }


    @Override
    protected void eol(final byte[] b, final int len) throws IOException {
        logger.append(PREFIX);
        logger.write(b, 0, len);
    }
}
