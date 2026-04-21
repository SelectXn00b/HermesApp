#version 300 es

precision highp float;
precision highp int;

out vec4 out_Color;

uniform vec4 u_EdgeColor;

void main()
{
	out_Color = u_EdgeColor;
}
