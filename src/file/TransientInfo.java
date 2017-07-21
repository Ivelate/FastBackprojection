package file;

import java.util.Random;

import org.lwjgl.util.vector.Vector3f;

/**
 * All info corresponding to a pixel of a transient image
 */
public class TransientInfo 
{	
	public final float[] color;
	public final float time;
	public final Vector3f point;
	public TransientInfo(float[] color, float time,Vector3f point)
	{
		this.color=color;
		this.time=time;
		this.point=point;
	}
}
