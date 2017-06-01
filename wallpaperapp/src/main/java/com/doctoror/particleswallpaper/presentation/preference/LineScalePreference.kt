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

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.OnLifecycleEvent
import android.content.Context
import android.util.AttributeSet
import com.doctoror.particleswallpaper.domain.repository.MutableSettingsRepository
import com.doctoror.particleswallpaper.presentation.di.Injector
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import javax.inject.Inject

/**
 * Created by Yaroslav Mytkalyk on 30.05.17.
 */
class LineScalePreference @JvmOverloads constructor
(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0)
    : SeekBarPreference(context, attrs, defStyle),
        MapperSeekbarPreference<Float>,
        LifecycleObserver {

    @Inject lateinit var settings: MutableSettingsRepository

    var disposable: Disposable? = null

    private val changeAction = Consumer<Float> { t ->
        if (t != null) {
            progress = transformToProgress(t)
        }
    }

    init {
        Injector.configComponent.inject(this)
        max = 70
        isPersistent = false
        setOnPreferenceChangeListener({ _, v ->
            if (v is Int) {
                val value = transformToRealValue(v)
                settings.setLineScale(value)
            }
            true
        })
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun subscribe() {
        disposable = settings.getLineScale().subscribe(changeAction)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun unsubscribe() {
        disposable?.dispose()
    }

    override fun transformToRealValue(progress: Int) = progress.toFloat() / 5f + 1f
    override fun transformToProgress(value: Float) = ((value - 1f) * 5f).toInt()
}