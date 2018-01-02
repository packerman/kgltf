#version 330 core

precision mediump float;

uniform vec4 color;
uniform sampler2D sampler;

in vec2 vTexCoord;

out vec4 fragColor;

void main() {
    fragColor = color * texture(sampler, vTexCoord);
}
