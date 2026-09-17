package com.sports.gamedayschedular.data.model

object DivisionDifficulty {
    val ageGroups = listOf("8U", "10U", "12U", "14U", "19U")
    val genders = listOf("Girls", "Boys")

    val headRefereeLevels = listOf(
        "8U Girls",
        "8U Boys",
        "10U Girls",
        "10U Boys",
        "12U Girls",
        "12U Boys",
        "14U Girls",
        "14U Boys",
        "19U Girls",
        "19U Boys"
    )

    fun getLevelForDivision(divisionName: String, gender: String): Int {
        val sanitizedDiv = divisionName.trim().lowercase()
        val sanitizedGender = gender.trim().lowercase()
        
        val fullName = if (sanitizedGender.contains("girl")) "$divisionName Girls" else "$divisionName Boys"
        val index = headRefereeLevels.indexOfFirst { it.lowercase() == fullName.lowercase() }
        
        return if (index != -1) index else {
            if (sanitizedDiv.contains("8u")) {
                if (sanitizedGender.contains("girl")) 0 else 1
            } else if (sanitizedDiv.contains("10u")) {
                if (sanitizedGender.contains("girl")) 2 else 3
            } else if (sanitizedDiv.contains("12u")) {
                if (sanitizedGender.contains("girl")) 4 else 5
            } else if (sanitizedDiv.contains("14u")) {
                if (sanitizedGender.contains("girl")) 6 else 7
            } else if (sanitizedDiv.contains("19u")) {
                if (sanitizedGender.contains("girl")) 8 else 9
            } else 0
        }
    }

    fun getDivisionStep(ageGroup: String): Int {
        val clean = ageGroup.uppercase().filter { it.isDigit() || it == 'U' }
        return when {
            clean.contains("8U") -> 0
            clean.contains("10U") -> 1
            clean.contains("12U") -> 2
            clean.contains("14U") -> 3
            clean.contains("16U") -> 4
            clean.contains("19U") -> 5
            else -> 0
        }
    }

    fun getDivisionStepForAge(age: Int): Int {
        return when {
            age < 8 -> 0
            age < 10 -> 1
            age < 12 -> 2
            age < 14 -> 3
            age < 16 -> 4
            else -> 5
        }
    }
}
