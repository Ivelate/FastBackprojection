package file;

import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;

import javax.imageio.ImageIO;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.util.vector.Vector3f;

import com.github.ivelate.JavaHDR.HDREncoder;
import com.github.ivelate.JavaHDR.HDRImage;
import com.github.ivelate.JavaHDR.HDRImageGrayscale;

/**
 * Transient Image. Contains the intensity values of a transient image, its laser associated, the wall points to which each pixel is projected, the time dilations, etc.
 * Provides methods to perform most typical calculations needed in transient images in an automatic way.
 */
public class TransientImage 
{	
	public final int width;
	public final int height;
	public final int channels; //Only one is used
	
	protected float maxValue=0;
	protected float minValue=Float.MAX_VALUE;
	
	protected final float timePerCoord;				//Time elapsed per temporal pixel coord
	protected final float intensityMultiplierUnit;	//Used in order to perform intensity correction
	protected float[][][] data; 					//data of the image
	
	protected float laserHitTime=0; 		//t0 of the image
	protected float[] wallCameraDilation;	//time dilation needed for each of the image spatial pixels in order to correct wall to cam delay
	protected Vector3f pointWallI;			//Leftmost point of the wall, corresponding to the y=0 image row
	protected Vector3f wallDirection;		//Spatial direction in which the camera projects the wall
	protected Vector3f wallNormal;			//wall normal. Is orthogonal to the wall direction
	protected Vector3f laser;				//Position of the laser projection causing this image
	protected float wallViewWidth;			//Width of the wall projection
	protected float pxHalfWidth;			//Half width of each pixel, in order to perform operations in the center all pixels, not on its left edges
		
	public TransientImage(int width,int height,int channels,float timePerCoord,float intensityMultiplierUnit,float[][][] data,float maxValue,float minValue)
	{
		this.width=width;
		this.height=height;
		this.channels=channels;
		this.timePerCoord=timePerCoord;
		this.intensityMultiplierUnit=intensityMultiplierUnit;
		this.minValue=minValue;
		this.maxValue=maxValue;
		if(data==null) this.data=new float[width][height][channels]; //RGBE
		else{
			if(data.length!=width||data[0].length!=height||data[0][0].length!=channels) {
				System.err.println("Unsupported array size (!!!!!!)");
				this.data=new float[width][height][channels];
			}
			else this.data=data;
		}
		this.wallCameraDilation=new float[height];
		this.wallViewWidth=this.height;
	}
	
	public void setLaserHitTime(float t)
	{
		this.laserHitTime=t;
	}
	
	public float getMaxValue()
	{
		return this.maxValue;
	}
	
	public float getMinValue()
	{
		return this.minValue;
	}
	
	public float getDeltaTime()
	{
		return this.timePerCoord;
	}
	
	public void setParamsForCamera(Vector3f cam,Vector3f lookTo,Vector3f wallDir,Vector3f wallNormal,float fov,Vector3f laser,int ystreak,float streakyratio,boolean unwarpCamera)
	{
		this.setParamsForCamera(cam, lookTo,wallDir,wallNormal, fov, laser, ystreak,streakyratio,0,unwarpCamera);
	}
	
	/**
	 *	Configs all scene parameters, calculating points of the wall captured by the camera and the time dilations 
	 */
	public void setParamsForCamera(Vector3f cam,Vector3f lookTo,Vector3f wallDir,Vector3f wallNormal,float fov,Vector3f laser,int ystreak,float streakyratio,float t0,boolean unwarpCamera)
	{
		float dwall=(float)Math.sqrt((Vector3f.sub(cam, lookTo, null)).lengthSquared());
		
		float semiWidth=(float)(Math.tan(fov/2)*dwall);
		float pxHalfHeight=semiWidth*streakyratio/this.height;
		float streakAbsY=semiWidth - pxHalfHeight -((((float)ystreak)*streakyratio)/this.height)*semiWidth*2; //Are the streaks upside-down or downside-upwards?
		this.wallDirection=wallDir;
		this.wallNormal=wallNormal;
		Vector3f wall_up=Vector3f.cross(this.wallNormal, this.wallDirection, null); //Already normalized

		pointWallI=new Vector3f(lookTo);
		wall_up.scale(streakAbsY);
		Vector3f.add(pointWallI,wall_up, pointWallI);
		wall_up.set(wallDirection);wall_up.scale(-semiWidth); //reuse wall_up to save memory
		Vector3f.add(pointWallI,wall_up, pointWallI);

		this.wallViewWidth=semiWidth*2;
		this.pxHalfWidth=wallViewWidth / (this.height*2);
		
		float dwallstreaksq=dwall*dwall + streakAbsY*streakAbsY;
		
		if(!unwarpCamera)
		{
			for(int i=0;i<this.wallCameraDilation.length;i++)
			{
				float x=(((float)(i)/this.wallCameraDilation.length)*this.wallViewWidth)-semiWidth + pxHalfWidth;
				this.wallCameraDilation[i]=(float)Math.sqrt(x*x + dwallstreaksq) - t0; 
			}
		}
		this.laser=laser;
	}
	
	public Vector3f getLaser()
	{
		return this.laser;
	}
	public Vector3f getWallNormal()
	{
		return this.wallNormal;
	}

	/**
	 * x: time , y: space
	 */
	public TransientInfo get(int x,int y)
	{
		return get(x,y,new Vector3f());
	}
	public TransientInfo get(int x,int y,Vector3f aux)
	{
		float time=x*this.timePerCoord - this.laserHitTime - this.wallCameraDilation[y];
		Vector3f point=getPointForCoord(y,aux);
		return new TransientInfo(this.data[x][y],time,point);
	}
	
	public Vector3f getPointForCoord(int y)
	{
		return getPointForCoord(y,new Vector3f());
	}
	public Vector3f getPointForCoord(int y,Vector3f aux)
	{
		aux.set(pointWallI.x+(((float)(y)/this.height)*wallViewWidth + pxHalfWidth)*wallDirection.x,
				pointWallI.y+(((float)(y)/this.height)*wallViewWidth + pxHalfWidth)*wallDirection.y,
				pointWallI.z+(((float)(y)/this.height)*wallViewWidth + pxHalfWidth)*wallDirection.z);
		return aux;
	}
	
	public float getIntensityForTime(int y,float time)
	{
		int x=(int)((time + this.laserHitTime + this.wallCameraDilation[y])/this.timePerCoord);
		if(x>=this.width||x<0) return 0;
		
		return this.data[x][y][0]; //|TODO more channels can be added, only channel 0 is used by now
	}
	
	/**
	 * To be used with caution. Treat the data returned by this method as a constant
	 */
	public float[][][] getInternalStorage()
	{
		return this.data;
	}
	
	/**
	 * Used as debug
	 * @throws IOException 
	 */
	public void printToFile(File outFile) throws IOException
	{
		printToFile(outFile,this.maxValue);
	}
	public void printToFile(File outFile,float maxValue) throws IOException
	{
		if(outFile.getName().toLowerCase().endsWith(".hdr"))
		{
			HDRImage img=new HDRImageGrayscale(width,height);
			for(int x=0;x<width;x++) for(int y=0;y<height;y++) {		
				float c=data[x][y][0]<maxValue?data[x][y][0]:0; //If intensity exceeds maximum intensity, image has been clamped
				img.setPixelValue(x, y, 0, c);
			}
			HDREncoder.writeHDR(img, outFile);
		}
		else
		{
			BufferedImage off_Image =
					  new BufferedImage(width, height,
					                    BufferedImage.TYPE_INT_RGB);
			
			for(int x=0;x<width;x++) for(int y=0;y<height;y++) {
	
				int c=data[x][y][0]<maxValue?(int)((data[x][y][0]*255) / maxValue):0; //If intensity exceeds maximum intensity, image has been clamped
				//|TODO unshit
				off_Image.setRGB(x, y, c<<16 | c<< 8 | c);
			}
		
			ImageIO.write(off_Image, "PNG", outFile);
		}
	}
	/*public int getAsTexture(int textureUnit)
	{
		int tex=GL11.glGenTextures();
		GL13.glActiveTexture(textureUnit);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D,tex);
		
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4); //4 bytes per component bby
		
		FloatBuffer buff=BufferUtils.createFloatBuffer(this.width*this.height*(this.channels>3?3:this.channels));
		//To float buff
		for(int h=0;h<this.height;h++)
		{
			for(int w=0;w<this.width;w++)
			{
				for(int c=0;c<this.channels&&c<3;c++)
				{
					buff.put(this.data[w][h][c]);
				}
			}
		}
		
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, this.width, this.height, 0, 
				GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buff);
	}*/
}
