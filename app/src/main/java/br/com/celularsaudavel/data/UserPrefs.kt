package br.com.celularsaudavel.data

import android.content.Context

/** Nome de quem usa o app e se a apresentação inicial já foi vista. */
class UserPrefs(context: Context) {
    private val p = context.getSharedPreferences("usuario", Context.MODE_PRIVATE)

    var name: String
        get() = p.getString("nome", "") ?: ""
        set(v) = p.edit().putString("nome", v.trim()).apply()

    var onboarded: Boolean
        get() = p.getBoolean("apresentacao", false)
        set(v) = p.edit().putBoolean("apresentacao", v).apply()

    /** Só o primeiro nome, para saudar. */
    val firstName: String get() = name.trim().substringBefore(' ')
}

/** "Bom dia", "Boa tarde" ou "Boa noite" conforme a hora. */
fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Bom dia"
    in 12..17 -> "Boa tarde"
    else -> "Boa noite"
}
