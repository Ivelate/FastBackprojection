package geometry;

public class Point 
{
	public float x;
	public float y;
	public float z;
	public Point(float x,float y,float z)
	{
		this.x=x;
		this.y=y;
		this.z=z;
	}
	@Override
	public boolean equals(Object o)
	{
		if(o instanceof Point)
		{
			Point p=(Point)o;
			if(this.x==p.x&&this.y==p.y&&this.z==p.z) return true;
		}
		return false;
	}
}
