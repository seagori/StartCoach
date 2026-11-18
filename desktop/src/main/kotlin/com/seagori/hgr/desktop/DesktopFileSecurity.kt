package com.seagori.hgr.desktop

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryFlag
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission

object DesktopFileSecurity {
    fun restrictToOwner(file: File, isDirectory: Boolean = file.isDirectory) {
        runCatching {
            val path = file.toPath()
            if (!Files.exists(path)) return
            if (applyPosix(path, isDirectory)) return
            applyAcl(path, isDirectory)
        }
    }

    private fun applyPosix(path: Path, isDirectory: Boolean): Boolean {
        val posix = Files.getFileAttributeView(path, PosixFileAttributeView::class.java) ?: return false
        val perms = mutableSetOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
        )
        if (isDirectory) perms += PosixFilePermission.OWNER_EXECUTE
        posix.setPermissions(perms)
        return true
    }

    private fun applyAcl(path: Path, isDirectory: Boolean) {
        val aclView = Files.getFileAttributeView(path, AclFileAttributeView::class.java) ?: return
        val owner = Files.getOwner(path) ?: return
        val builder = AclEntry.newBuilder()
            .setType(AclEntryType.ALLOW)
            .setPrincipal(owner)
            .setPermissions(AclEntryPermission.values().toSet())
        if (isDirectory) {
            builder.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT)
        }
        aclView.acl = listOf(builder.build())
    }
}
