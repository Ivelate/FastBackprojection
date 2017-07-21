package core;

public class BoxBounds 
{
	public final float xi,yi,zi;
	public final float sx,sy,sz;
	public final int resolution;
	
	public BoxBounds(float xi,float yi,float zi,float scale,int resolution)
	{
		this.xi=xi; this.yi=yi; this.zi=zi;
		this.sx=scale; this.sy=scale; this.sz=scale;
		this.resolution=resolution;
	}
	public BoxBounds(float xi,float yi,float zi,float sx,float sy,float sz,int resolution)
	{
		this.xi=xi; this.yi=yi; this.zi=zi;
		this.sx=sx; this.sy=sy; this.sz=sz;
		this.resolution=resolution;
	}
	
}
