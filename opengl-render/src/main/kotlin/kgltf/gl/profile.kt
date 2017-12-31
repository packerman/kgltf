package kgltf.gl

enum class GLProfile(val majorVersion: Int,
                     val minorVersion: Int,
                     val type: ProfileType,
                     val forwardCompatible: Boolean) {

    OpenGl33(3, 3, ProfileType.Core, true),
    OpenGl21(2, 1, ProfileType.Any, false);

    init {
        require(equalOrAbove(2, 1))
        require(equalOrAbove(3, 2) || type == ProfileType.Any)
        require(equalOrAbove(3, 0) || !forwardCompatible)
    }

    override fun toString(): String {
        return "GL $majorVersion.$minorVersion (type=$type, forwardCompatible=$forwardCompatible)"
    }
}

enum class ProfileType {
    Core,
    Compatible,
    Any
}

fun GLProfile.equalOrAbove(major: Int, minor: Int): Boolean {
    return when {
        majorVersion > major -> true
        majorVersion == major && minorVersion >= minor -> true
        else -> false
    }
}

interface ProfileFilter {
    fun isProfileAccepted(profile: GLProfile): Boolean = true
}

class FilterList(val filters: List<ProfileFilter>) : ProfileFilter {
    override fun isProfileAccepted(profile: GLProfile): Boolean =
            filters.all { it.isProfileAccepted(profile) }
}
