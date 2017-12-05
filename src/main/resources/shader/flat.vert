#version 330 core

layout(location = 0) in vec4 position;

uniform vec4 color;
uniform mat4 mvp;

out vec4 vColor;

void main() {
    gl_Position = mvp * position;
    vColor = color;
}