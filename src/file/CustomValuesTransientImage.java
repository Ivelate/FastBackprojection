package file;

import org.lwjgl.util.vector.Vector3f;

/**
 * Transient image which doesn't use a camera. Instead, all wall positions are provided to it. Used when the camera isn't orthogonal to the wall
 * 
 * Counter of day-wasting bugs that this class caused: 2 (updated 07/04/2017)
 */
public class CustomValuesTransientImage extends TransientImage
{
	private Vector3f[] customWallPoints;
	public CustomValuesTransientImage(int width,int height,int channels,float timePerCoord,float intensityMultiplierUnit,float[][][] data,float maxValue,float minValue,Vector3f[] customWallPoints)
	{
		super(width,height,channels,timePerCoord,intensityMultiplierUnit,data,maxValue,minValue);
		this.customWallPoints=customWallPoints;
	}
	
	@Override
	public Vector3f getPointForCoord(int y)
	{
		return customWallPoints[y];
	}
	/**
	 * Aaaand another reason for not doing bad programming guys. Complete-overriding classes are bad if you extend the parent class methods. Well, solved
	 */
	@Override
	public Vector3f getPointForCoord(int y,Vector3f aux)
	{
		return customWallPoints[y];
	}
	/**
	 * wallDir, lookTo, fov and ystreak are unneeded
	 */
	@Override
	public void setParamsForCamera(Vector3f cam,Vector3f lookTo,Vector3f wallDir,Vector3f wallNormal,float fov,Vector3f laser,int ystreak,float streakyratio,boolean unwarpCamera)
	{
		this.setParamsForCamera(cam, lookTo, wallDir, wallNormal, fov, laser, ystreak,streakyratio,0,unwarpCamera);
	}
	@Override
	public void setParamsForCamera(Vector3f cam,Vector3f lookTo,Vector3f wallDir,Vector3f wallNormal,float fov,Vector3f laser,int ystreak,float streakyratio,float t0,boolean unwarpCamera)
	{
		pointWallI=customWallPoints[0];
		this.wallDirection=wallDir;
		this.wallNormal=wallNormal;
		this.wallViewWidth=Vector3f.sub(customWallPoints[0], customWallPoints[customWallPoints.length-1], null).length();
		this.pxHalfWidth=wallViewWidth / (this.height*2);
		
		if(!unwarpCamera)
		{
			for(int i=0;i<this.wallCameraDilation.length;i++)
			{
				this.wallCameraDilation[i]=Vector3f.sub(cam, customWallPoints[i], null).length() - t0;
			}
		}
		this.laser=laser;
	}
}
