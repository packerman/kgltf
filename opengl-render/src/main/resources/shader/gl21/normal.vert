#version 120

attribute vec4 position;
attribute vec3 normal;

uniform mat4 modelViewProjectionMatrix;
uniform mat4 normalMatrix;

varying vec4 vColor;

void main() {
    gl_Position = modelViewProjectionMatrix * position;
    vec3 normalized = normalize(vec3(normalMatrix * vec4(normal, 0.0)));
    vColor = vec4((normalized.x + 1.0)/2.0, (normalized.y + 1.0)/2.0, (normalized.z + 1.0)/2.0, 1.0);
}
