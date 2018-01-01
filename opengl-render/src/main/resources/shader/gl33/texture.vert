#version 330 core

layout(location = 0) in vec4 position;
layout(location = 1) in vec2 texCoord;

uniform mat4 modelViewProjectionMatrix;

out vec2 vTexCoord;

void main() {
    vTexCoord = texCoord;
    gl_Position = modelViewProjectionMatrix * position;
}
