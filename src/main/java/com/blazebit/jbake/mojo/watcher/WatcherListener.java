/*
 * Copyright 2016 Blazebit.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.blazebit.jbake.mojo.watcher;

import java.nio.file.Path;

/**
 * 
 * @author Christian Beikov
 */
public interface WatcherListener {

    public void refreshQueued();

    public void refresh();

    public void created(Path path);

    public void deleted(Path path);

    public void modified(Path path);
}
