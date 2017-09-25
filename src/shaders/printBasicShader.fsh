#version 440 core

const vec3 orthoLight=normalize(vec3(1,0,1));

in vec3 Normal;

out vec4 color;

void main()
{
	float illumination=max(dot(orthoLight,Normal)*0.7,0) + 0.3;
	color=vec4(illumination,illumination,illumination,1);
	//color=vec4((normalInd==0||normalInd==3)?1:0,(normalInd==1||normalInd==3)?1:0,(normalInd==2)?1:0,1);
}