#version 440 core

layout(binding=0 , r32ui) uniform readonly uimage3D voxelStorage;
uniform int voxelRes;

layout(location=0) out uint maxColor;
layout(location=1) out uint maxDepth;
void main()
{
//uint accum=0;
	uint max=0;
	uint maxv=0;
	//LAPLACIAN
	if(gl_FragCoord.x>0&&gl_FragCoord.x<voxelRes-1&&gl_FragCoord.y>0&&gl_FragCoord.y<voxelRes-1)
	{
		for(uint i=1;i<voxelRes-1;i++)
		{
			uint currp=imageLoad(voxelStorage,ivec3(gl_FragCoord.x,gl_FragCoord.y,i-1)).x;
			uint currm=imageLoad(voxelStorage,ivec3(gl_FragCoord.x,gl_FragCoord.y,i+1)).x;
			uint curr=imageLoad(voxelStorage,ivec3(gl_FragCoord.x,gl_FragCoord.y,i)).x;
			
			uint currpx=imageLoad(voxelStorage,ivec3(gl_FragCoord.x+1,gl_FragCoord.y,i)).x;
			uint currmx=imageLoad(voxelStorage,ivec3(gl_FragCoord.x-1,gl_FragCoord.y,i)).x;
			
			uint currpy=imageLoad(voxelStorage,ivec3(gl_FragCoord.x,gl_FragCoord.y+1,i)).x;
			uint currmy=imageLoad(voxelStorage,ivec3(gl_FragCoord.x,gl_FragCoord.y-1,i)).x;
			
			curr=curr*6;
			uint comps=currp+currm+currpx+currmx+currpy+currpx;
			if(curr > comps)
			{
				curr=curr - comps;
				if(max<curr) {
					max=curr;
					maxv=i;
				}
			}
			//accum+=curr;
		}
	}
	//max=imageLoad(voxelStorage,ivec3(gl_FragCoord.x,gl_FragCoord.y,100)).x*2 - imageLoad(voxelStorage,ivec3(gl_FragCoord.x,gl_FragCoord.y,99)).x -imageLoad(voxelStorage,ivec3(gl_FragCoord.x,gl_FragCoord.y,101)).x;
	//maxv=0;
	
	maxColor=max;//accum;
	maxDepth=maxv;
}