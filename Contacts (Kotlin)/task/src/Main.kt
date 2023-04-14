package contacts

import java.io.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun main(args: Array<String>) {
    Contacts(
        if (args.isEmpty()) "" else args[0]).actions()
}

abstract class Contact : Serializable {
    companion object {
        private const val NUMBER = "number"
        const val TIME_ADDED = "time created"
        const val TIME_EDIT = "time last edit"
        val PROPERTY = listOf(NUMBER)
        val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")!!
        const val NO_NUMBER = "[no number]"
        const val NO_DATA = "[no data]"
        fun matchNumber(number: String) =
            "^\\+?(\\(\\w+\\)|\\w+([ -]?\\(\\w{2,}\\))?)([ -]?\\w{2,})*\$"
                .toRegex().matches(number)
        private fun now() = LocalDateTime.now()!!
    }

    private val map = mutableMapOf<String, String>()
    init {
        FORMATTER.format(now()).let {
            map[TIME_ADDED] = it
            map[TIME_EDIT] = it
        }
    }
    open fun checkup(name: String,
                      number: String): String? =
        when (name) {
        NUMBER ->
            if (matchNumber(number)) number
            else NO_NUMBER.also { println("Wrong number format!") }
        else -> {
            if (name in access()) number
            else println("Unknown field [$name]").let { null }
        }
    }
    open operator fun set(name: String, value: String) = checkup(name, value)
            ?.let { map[name] = it }
            .also { map[TIME_EDIT] = FORMATTER.format(now()) }
    open operator fun get(name: String): String = map[name] ?: NO_DATA
    open fun access() = PROPERTY
    open fun properties() = access() + listOf(TIME_ADDED, TIME_EDIT)
    abstract val short: String
}

class Man : Contact() {
    companion object {
        const val NAME = "name"
        const val SURNAME = "surname"
        const val BIRTHDATE = "birth date"
        const val GENDER = "gender"
        val PROPERTY = listOf(NAME, SURNAME, BIRTHDATE, GENDER) + Contact.PROPERTY
        val GENDERS = setOf("M", "F")
    }
    override val short get() = "${get(NAME)} ${get(SURNAME)}"

    override fun checkup(name: String, value: String): String? =
        when (name) {
        GENDER ->
            if (value in GENDERS) value
            else NO_DATA.also { println("Bad gender!") }
        BIRTHDATE -> try {
            LocalDate.parse(value).toString()
        } catch (_: Exception) {
            NO_DATA.also { println("Bad birth date!") }
        }
        else -> super.checkup(name, value)
    }
    override fun access() = PROPERTY
}

class Company : Contact() {
    companion object {
        const val COMPANY_NAME = "organization name"
        private const val ADDRESS = "address"
        val PROPERTY = listOf(COMPANY_NAME, ADDRESS) + Contact.PROPERTY
    }
    override val short get() = get(COMPANY_NAME)
    override fun access() = PROPERTY
}

class Contacts(private var file: String = "") {
    companion object {
        fun userChoice(str: String) = print(str).run { readln() }
    }
    private val contacts = mutableListOf<Contact>()
    fun actions() {
        if (file.isNotEmpty()) {
            println("open $file")
            read()
        }
        while (true) {
            when (val act = userChoice(
                "[menu] Enter action (add, list, search, count, exit): ")) {
                "add" -> add()
                "list" -> list()?.let { record(it) }
                "search" -> search()?.let { record(it) }
                "count" -> count()
                "exit" -> break
                else -> println("Unknown command [$act]")
            }
            println()
        }
    }
    private fun count() = println("The Phone Book has ${contacts.size} records.")

    private fun query(): List<Contact> {
        val query = userChoice("Enter search query: ")
        val list: List<Contact> = contacts.filter {
                contact -> contact.access().any {
            contact[it].contains(query, true)
                }
        }
        println("Found ${list.size} results:")
        list.short()
        return list
    }
    private fun search(): Contact? {
        var list = query()
        while (true) {
            when (val act =
                userChoice("[search] Enter action ([number], back, again): ")) {
                "back" -> return null
                "again" -> list = query()
                else -> list[act].also {
                    if (it == null) println("Wrong number [$act]")
                else return it }
            }
            println()
        }
    }
    private fun list(): Contact? {
        contacts.short()
        while (true) {
            when (val act =
                userChoice("[list] Enter action ([number], back): ")) {
                "back" -> return null
                else -> contacts[act].also {
                    if (it == null) println("Wrong number [$act]")
                    else return it }
            }
            println()
        }
    }
    private fun record(contact: Contact) {
        while (true) {
            info(contact)
            when (val act =
                userChoice("[record] Enter action (edit, delete, menu): ")) {
                "edit" -> edit(contact)
                "delete" -> delete(contact).also { return }
                "menu" -> return
                else -> println("Unknown command [$act]")
            }
        }
    }
    private fun info(contact: Contact) {
        for (name in contact.properties()) {
            println(name[0].uppercase() +
                    "${name.substring(1)}: " +
                    contact[name]
            )
        }
        println()
    }
    private fun edit(contact: Contact) {
        info(contact)
        val field = userChoice("Select a field " +
                    "(${contact.access().joinToString(", ")}): ")
        if (field in contact.access()) {
            contact[field] = userChoice("Enter $field: ")
            save()
        } else {
            println("Unknown field [$field]")
        }
    }
    private fun delete(contact: Contact) {
        contacts.remove(contact)
        println("The record removed!")
        save()
    }
    private fun add() {
        when (val type = userChoice("Enter the type (person, organization): ")) {
            "person" -> Man()
            "organization" -> Company()
            else -> {
                println("Unknown type [$type]")
                return
            } }
            .apply { access().forEach { this[it] = userChoice("Enter the $it: ") } }
            .also(contacts::add)
        println("The record added.")
        save()
    }
    private fun save() {
        if (file.isNotEmpty()) {
            ObjectOutputStream(FileOutputStream(file)).use { it.writeObject(contacts) }
            println("Saved")
        }
    }
    private fun read() {
        try {
            ObjectInputStream(FileInputStream(file)).use {
                    reader -> reader.readObject().let {
                    if (it is List<*>)
                        for (e in it)
                            if (e is Contact) contacts.add(e) }
            }
        } catch (e: Exception) {
            println(e)
            file = ""
        }
    }
    private operator fun List<Contact>.get(s: String) =
        try {
            this[s.toInt() - 1]
    } catch (_: Exception) {
        null
    }

    private fun List<Contact>.short() {
        for (i in 0..lastIndex) {
            println("${i + 1}. ${this[i].short}")
        }
        println()
    }
}


