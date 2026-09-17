package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

object ContactActions {

    /** Returns (displayName, number) for the best match, or null. */
    fun findNumber(ctx: Context, rawName: String): Pair<String, String>? {
        val aliases = Prefs.getAliases(ctx)
        val name = aliases[rawName.lowercase()] ?: rawName
        val resolver = ctx.contentResolver
        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        ) ?: return null

        cursor.use {
            if (it.moveToFirst()) {
                val displayName = it.getString(0)
                val number = it.getString(1)
                return displayName to number
            }
        }
        return null
    }

    fun call(ctx: Context, number: String) {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }
}
