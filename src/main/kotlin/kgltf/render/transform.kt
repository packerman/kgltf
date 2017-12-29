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

interface Camera {
    val projectionMatrix: Matrix4fc
    fun update(aspectRatio: Float)
}

class PerspectiveCamera(var aspectRatio: Float, val yFov: Float, val zNear: Float, val zFar: Float = Float.POSITIVE_INFINITY) : Camera {

    private val _projectionMatrix = Matrix4f()

    override val projectionMatrix: Matrix4fc = _projectionMatrix

    override fun update(aspectRatio: Float) {
        this.aspectRatio = aspectRatio
        _projectionMatrix.setPerspective(yFov, aspectRatio, zNear, zFar)
    }
}

class OrthographicCamera(var xMag: Float, var yMag: Float, val zNear: Float, val zFar: Float) : Camera {

    private val _projectionMatrix = Matrix4f()

    override val projectionMatrix: Matrix4fc = _projectionMatrix

    override fun update(aspectRatio: Float) {
        if (aspectRatio > xMag / yMag) {
            _projectionMatrix.setOrthoSymmetric(2 * yMag * aspectRatio, 2 * yMag, zNear, zFar)
        } else {
            _projectionMatrix.setOrthoSymmetric(2 * xMag, 2 * xMag / aspectRatio, zNear, zFar)
        }
    }
}

class IdentityCamera : Camera {
    override val projectionMatrix: Matrix4fc = Matrix4f()
    override fun update(aspectRatio: Float) {}
}
