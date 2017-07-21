package file;

import java.io.File;

/**
 * Used to check which filenames are valid transient slice files. Can be customized by the user.
 */
public class AcceptedFileName 
{
	private int streakUnderscoreLocation;
	private int underscoreNum;
	private String endsWith;
	
	public AcceptedFileName(int underscoreNum,int streakUnderscoreLocation,String endsWith)
	{
		this.streakUnderscoreLocation=streakUnderscoreLocation;
		this.underscoreNum=underscoreNum;
		this.endsWith=endsWith;
	}
	public boolean isAccepted(File file)
	{
		if(file.getName().endsWith(this.endsWith)){
			int occ=0;
			for(int i=0;i<file.getName().length();i++)
			{
				if(file.getName().charAt(i)=='_') occ++;
			}
			
			if(occ==this.underscoreNum) return true;
		}
		return false;
	}
	public StreakLaser getStreakAndLaser(File file)
	{
		String[] splittedName=file.getName().split("_");
		int laserIndex=Integer.parseInt(splittedName[1])-1;
		int streak=Integer.parseInt(splittedName[this.streakUnderscoreLocation].split("\\.")[0]);
		return new StreakLaser(streak,laserIndex);
	}
	public class StreakLaser
	{
		public int streak;
		public int laser;
		public StreakLaser(int streak,int laser){this.streak=streak;this.laser=laser;}
	}
}
