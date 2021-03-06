/*
 * Copyright (c) 2013 SysGears, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sysgears.grain.compass

import com.google.inject.AbstractModule
import com.google.inject.Injector
import com.google.inject.Provides
import com.sysgears.grain.config.Config
import com.sysgears.grain.config.ImplBinder

/**
 * Package-specific IoC config 
 */
class CompassModule extends AbstractModule {

    @Provides @javax.inject.Singleton
    public Compass provideCompass(Injector injector,
            FakeCompass fake, RubyCompass ruby, ShellCompass shell) {
        new ImplBinder<Compass>(Compass.class, 'features.compass',
                [default: fake, auto: ruby, jruby: ruby, ruby: ruby, shell: shell], injector).proxy
    }

    @Override
    protected void configure() {
    }
}
