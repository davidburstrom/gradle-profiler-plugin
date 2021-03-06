/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.profiler.internal;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class EnableProfilingTask extends DefaultTask {

    private final ListProperty<String> asyncProfilerParameters;
    private final DirectoryProperty asyncProfilerLocation;
    private final RegularFileProperty profilingInitScriptFile;
    private final RegularFileProperty profilingPreferencesFile;

    public EnableProfilingTask() {
        ObjectFactory objects = getProject().getObjects();
        this.asyncProfilerParameters = objects.listProperty(String.class);
        this.asyncProfilerLocation = objects.directoryProperty();
        this.profilingInitScriptFile = objects.fileProperty();
        this.profilingPreferencesFile = objects.fileProperty();
    }

    @InputDirectory
    public DirectoryProperty getAsyncProfilerLocation() {
        return asyncProfilerLocation;
    }

    @Input
    public ListProperty<String> getAsyncProfilerParameters() {
        return asyncProfilerParameters;
    }

    @OutputFile
    public RegularFileProperty getProfilingInitScriptFile() {
        return profilingInitScriptFile;
    }

    @OutputFile
    public RegularFileProperty getProfilingPreferencesFile() {
        return profilingPreferencesFile;
    }

    @TaskAction
    public void enableProfiling() {
        verifyConfiguration();
        try {
            writeProfileScriptToGradleInit();
            appendRootLocationToPreferences();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void verifyConfiguration() {
        File asyncProfilerLocation = getAsyncProfilerLocation().get().getAsFile();
        File[] contents = asyncProfilerLocation.listFiles();
        if (contents == null || contents.length == 0) {
            throw new org.gradle.api.InvalidUserCodeException("Async Profiler location not declared.\n\n" +
                    "Download Async Profiler (https://github.com/jvm-profiling-tools/async-profiler) and place it in the '" + asyncProfilerLocation + "' directory.\n" +
                    "Or configure a custom installation path with:\n\n" +
                    "profiler {\n" +
                    "  asyncProfilerLocation = \"/path/to/async/profiler\"\n" +
                    "}");
        }
    }

    private void writeProfileScriptToGradleInit() throws IOException {
        File profileScript = getProfilingInitScriptFile().get().getAsFile();
        String profileScriptContent = CharStreams.toString(new InputStreamReader(getClass().getResource("/profiler.gradle").openStream(), Charsets.UTF_8));
        profileScriptContent = profileScriptContent
                .replaceAll("%async.profiler.location%", getAsyncProfilerLocation().get().getAsFile().getAbsolutePath())
                .replaceAll("%global.preferences.file%", Constants.LOCATION_GLOBAL_PREFERENCES_FILE)
                .replaceAll("%async.profiler.parameters%", getAsyncProfilerParameters().get().stream().collect(Collectors.joining(" ")));
        Files.asCharSink(profileScript, Charsets.UTF_8).write(profileScriptContent);
    }

    private void appendRootLocationToPreferences() throws IOException {
        File preferences = getProfilingPreferencesFile().get().getAsFile();
        String content = preferences.exists() ? CharStreams.toString(new FileReader(preferences)) : "";
        String rootLocation = getProject().getRootProject().getProjectDir().getAbsolutePath();
        if (!content.contains(rootLocation)) {
            Files.asCharSink(preferences, Charsets.UTF_8, FileWriteMode.APPEND).write(Constants.LINE_SEPARATOR + rootLocation);
        }
    }
}
