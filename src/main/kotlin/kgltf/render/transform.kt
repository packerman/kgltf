package kgltf.render

import org.joml.*

class Transform {

    private val _matrix = Matrix4f()
    var matrix: Matrix4fc
        get() {
            update()
            return _matrix
        }
        set(value) {
            _matrix.set(value)
            _matrix.getTranslation(_translation)
            _matrix.getUnnormalizedRotation(_rotation)
            _matrix.getScale(_scale)
            needsUpdate = false
        }

    private var needsUpdate = true

    fun update() {
        if (needsUpdate) {
            _matrix
                    .scaling(_scale)
                    .rotate(_rotation)
                    .translate(_translation)
            needsUpdate = false
        }
    }

    private val _translation = Vector3f()
    var translation: Vector3fc
        get() = _translation
        set(value) {
            _translation.set(value)
            needsUpdate = true
        }

    private val _rotation = Quaternionf()
    var rotation: Quaternionfc
        get() = _rotation
        set(value) {
            _rotation.set(value)
            needsUpdate = true
        }

    private val _scale = Vector3f(1f)
    var scale: Vector3fc
        get() = _scale
        set(value) {
            _scale.set(value)
            needsUpdate = true
        }
}
