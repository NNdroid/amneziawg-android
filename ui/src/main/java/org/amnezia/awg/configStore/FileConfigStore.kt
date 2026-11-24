/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.configStore

import android.content.Context
import android.util.Log
import org.amnezia.awg.R
import org.amnezia.awg.config.BadConfigException
import org.amnezia.awg.config.Config
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Configuration store that uses a `awg-quick`-style file for each configured tunnel.
 */
class FileConfigStore(private val context: Context) : ConfigStore {
    @Throws(IOException::class)
    override fun create(name: String, config: Config): Config {
        Log.d(TAG, "Creating configuration for tunnel $name")
        logIp4p("create", name, config)
        val file = fileFor(name)
        if (!file.createNewFile())
            throw IOException(context.getString(R.string.config_file_exists_error, file.name))
        FileOutputStream(file, false).use { it.write(config.toAwgQuickString().toByteArray(StandardCharsets.UTF_8)) }
        return config
    }

    @Throws(IOException::class)
    override fun delete(name: String) {
        Log.d(TAG, "Deleting configuration for tunnel $name")
        val file = fileFor(name)
        if (!file.delete())
            throw IOException(context.getString(R.string.config_delete_error, file.name))
    }

    override fun enumerate(): Set<String> {
        return context.fileList()
            .filter { it.endsWith(".conf") }
            .map { it.substring(0, it.length - ".conf".length) }
            .toSet()
    }

    private fun fileFor(name: String): File {
        return File(context.filesDir, "$name.conf")
    }

    @Throws(BadConfigException::class, IOException::class)
    override fun load(name: String): Config {
        val config = FileInputStream(fileFor(name)).use { stream -> Config.parse(stream) }
        logIp4p("load", name, config)
        return config
    }

    @Throws(IOException::class)
    override fun rename(name: String, replacement: String) {
        Log.d(TAG, "Renaming configuration for tunnel $name to $replacement")
        val file = fileFor(name)
        val replacementFile = fileFor(replacement)
        if (!replacementFile.createNewFile()) throw IOException(context.getString(R.string.config_exists_error, replacement))
        if (!file.renameTo(replacementFile)) {
            if (!replacementFile.delete()) Log.w(TAG, "Couldn't delete marker file for new name $replacement")
            throw IOException(context.getString(R.string.config_rename_error, file.name))
        }
    }

    @Throws(IOException::class)
    override fun save(name: String, config: Config): Config {
        Log.d(TAG, "Saving configuration for tunnel $name")
        logIp4p("save", name, config)
        val file = fileFor(name)
        if (!file.isFile)
            throw FileNotFoundException(context.getString(R.string.config_not_found_error, file.name))
        FileOutputStream(file, false).use { stream -> stream.write(config.toAwgQuickString().toByteArray(StandardCharsets.UTF_8)) }
        return config
    }

    /**
     * Logs the IP4P-relevant fields (bootstrap DNS and IP4P prefix) for
     * diagnostics. These fields were previously not persisted in the
     * awg-quick config format, which caused them to appear empty after a
     * save/reload cycle.
     */
    private fun logIp4p(action: String, name: String, config: Config) {
        val interfaze = config.getInterface()
        Log.d(
            TAG,
            "IP4P $action tunnel=$name bootstrap_dns=${interfaze.bootstrapDns.orElse("<none>")} " +
                "ip4p_prefix=${interfaze.ip4pPrefix.orElse("<none>")}"
        )
    }

    companion object {
        private const val TAG = "AmneziaWG/FileConfigStore"
    }
}
