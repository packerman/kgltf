#version 330

layout(location = 0) in vec3 position;
layout(location = 2) in vec3 normal;

uniform mat3 normalMatrix;
uniform mat4 modelViewMatrix;
uniform mat4 modelViewProjectionMatrix;

out vec3 vEyeCoord;
out vec3 vNormal;

void main() {
    vNormal = normalMatrix * normal;
    vEyeCoord = vec3(modelViewMatrix * vec4(position, 1));
	gl_Position = modelViewProjectionMatrix * vec4(position, 1);
}
