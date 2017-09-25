#version 440 core


in vec2 location;

void main()
{
	gl_Position=vec4(location.xy,0,1);
}