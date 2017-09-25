#version 440 core
out vec4 outcolor;
in vec3 Color;
void main()
{
	outcolor=vec4(Color.xyz,1);
}