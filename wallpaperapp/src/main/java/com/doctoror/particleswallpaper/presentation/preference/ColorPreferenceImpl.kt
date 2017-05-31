/*
 * Copyright (C) 2017 Yaroslav Mytkalyk
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
package com.doctoror.particleswallpaper.presentation.preference

import android.content.Context
import android.util.AttributeSet
import com.doctoror.particleswallpaper.data.repository.SettingsRepositoryFactory
import com.doctoror.particleswallpaper.domain.repository.MutableSettingsRepository
import com.doctoror.particleswallpaper.domain.repository.SettingsRepository

/**
 * Created by Yaroslav Mytkalyk on 30.05.17.
 */
class ColorPreferenceImpl @JvmOverloads constructor
(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0)
    : ColorPreferenceNoPreview(context, attrs) {

    val settings: MutableSettingsRepository
            = SettingsRepositoryFactory.provideMutable(context)

    val defaults: SettingsRepository = SettingsRepositoryFactory.provideDefault()

    init {
        isPersistent = false
        setDefaultValue(settings.getColor().blockingFirst())
        setOnPreferenceChangeListener({ _, v ->
            val color = v as? Int ?: defaults.getColor().blockingFirst()
            settings.setColor(color)
            true
        })
    }

    override fun getPersistedInt(defaultReturnValue: Int): Int {
        return settings.getColor().blockingFirst()
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
        color = settings.getColor().blockingFirst()
    }
}