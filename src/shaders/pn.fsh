#version 440 core

layout(binding=0 , r32ui) uniform uimage3D voxelStorage;

uniform int voxelRes;

in VertexData {
    vec3 Normal;
    flat int dominantAxis;
    flat float intensity;
} VertexIn;
out vec4 color;

void main()
{
	//color=vec4(1,0,0,1)*(0.8*dot(Normal,normalize(vec3(1,0,1)))+0.2);
	color=vec4(normalize(VertexIn.Normal.xyz),1);
	//color=vec4(float(VertexIn.intensity)/255,0,0,1);
	//color=vec4(0,0,1,1);
	//color=vec4(VertexIn.dominantAxis==0?1:0,VertexIn.dominantAxis==1?1:0,VertexIn.dominantAxis==2?1:0,1);
	//color=vec4(0,0,gl_FragCoord.z,1);
	
	ivec3 finalScreenPos=ivec3(gl_FragCoord.xy,gl_FragCoord.z*voxelRes);
	if(VertexIn.dominantAxis==1) finalScreenPos=finalScreenPos.xzy;
	else if(VertexIn.dominantAxis==2) finalScreenPos=finalScreenPos.zyx;
	
	//uvec4 data=imageLoad(voxelStorage,finalScreenPos);
	//color=vec4(data.x>0?1:0,0,0,1);
	
	imageAtomicAdd(voxelStorage, finalScreenPos, int(VertexIn.intensity));
	//imageStore(voxelStorage,finalScreenPos,uvec4(2,2,2,2));

	/*uvec4 data2=imageLoad(voxelStorage,finalScreenPos);
	color=vec4(float(data2.x)/100,data2.x==0?1:0,0,1);*/
	//color=vec4(1,0,0,1);
}