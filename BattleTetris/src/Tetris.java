/** Tetris.java
 * 
 * Variables:
 * 	MyBoundedEnv env, env2
 * 		env stores the entire board's state.
 * 		env2 stores the upcoming Tetrad
 * 	BlockDisplay display, display2
 * 		display shows env
 * 		display2 shows env2
 * 	Tetrad rad, rad2
 * 		rad is the currently active Tetrad.
 * 			Manipulate this with the arrow keys. (or WASD)
 * 		rad2 is the upcoming Tetrad
 * 	int score
 * 		How many points you've gotten in this game. We will not go into the scoring system.
 * 		Increase by:
 * 			pressing down
 * 			getting a new block
 * 			clearing rows. More rows leads to a lot more points.
 * 	int level
 * 		Determines how fast the game will run. At lvl 100, there is no delay as the Tetrad falls.
 * 		Your level approximately solves level * level * 1000 = score 
 * 	int rowsMoved
 * 		keeps track of how many rows the current piece has moved.
 * 		(This information is not very important.)
 * 		Is set to some negative value in order to trigger Game Over.
 * 	boolean paused
 * 		when paused, no commands are accepted.
 * 		press P to unpause
 * 
 * 
 * How it plays:
 * 	while(!notLost())
 * 		play();
 * 
 * 	notLost() just checks if rowsMoved is negative or not.
 * 		rowsMoved is only set to a negative number upon losing; when a new Tetrad overlaps
 * 			with other pieces on the board.
 * 
 * 	play()
 * 		moves the current Tetrad down a row.
 * 		If it can't, it releases the tetrad and attempts to clear rows.
 * 		If r rows are cleared, blocks above are moved down by r rows
 * 		Additionally, if an opponent exists, opp.increasePendingRows(r - 1)
 * 
 * 		Swap out the current one for the coming one and take a new Tetrad
 * 			If the new tetrad overlaps, then set rowsMoved = -1, so that notLost() => false
 * 		Now that you're done with your movement, addPendingRows() that your opponent may have sent.
 * 			addPendingRows just adds the requisite number of rows from the bottom up, missing 1 tile
 * 		After adding them, resetPendingRows(), resets the variable, pendingRows, to 0.
 * 
 * restart()
 * 		clears the board of all pieces, and resets chosen Tetrads
 * 
 * act()
 * 		moves the active Tetrad based on keys currently being pressed.
 * 		(those keys have their corresponding arrows[] variable set to true)
 * 
 * is an ArrowListener
 * 	arrows are handled very straightforwardly.
 * 
 */

import java.awt.*;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;

// Used to display the contents of a game board
public class Tetris implements ArrowListener
{
	private MyBoundedEnv env;
	private BlockDisplay display;
	private Tetrad rad;

	private MyBoundedEnv env2;
	private BlockDisplay display2;
	private Tetrad rad2;

	private int score;
	private int level;
	private int[] stats = new int[5]; // num 1's, 2's, 3's, 4's, and rowsSent
	private int rowsMoved;
	private boolean paused;
	private JFrame frame;
	private JPanel panel;

	private boolean[] arrows; // 0 is up, 1 is left, 2 is right, 3 is down

	public static int GAME_ROWS = 22;
	public static int GAME_COLS = 10;

	private int pendingRows = 0;

	private Tetris opp = null;
	public TetrisAI ai = null;

	private PowerUp currentPowerUp = null;
	
	public static void main(String[] args)
	{
		new Tetris();
		System.out.println("Game over!");
	}

	public Tetris()
	{
		this(false);
	}
	
	public Tetris(boolean runLater)
	{
		arrows = new boolean[4];
		for (int i = 0; i < arrows.length; i++)
			arrows[i] = false;

		env=new MyBoundedEnv(GAME_ROWS,GAME_COLS);
		display=new JPanelBlockDisplay(env);
		env2=new MyBoundedEnv(4,GAME_COLS);
		display2=new JPanelBlockDisplay(env2);

		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.PAGE_AXIS));
		p.add(display2.getPanel());
		p.add(display.getPanel());
		p.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		panel = p;

		frame = new JFrame();
		if (!runLater)
		{
			frame.setContentPane(p);
			frame.addKeyListener(display);

			frame.pack();
			frame.setVisible(true);
		}

		score=0;
		level=1;
		frame.setTitle("Tetris! Level: "+level+" Score: "+score);
		display.setArrowListener(this);
		rad2=new Tetrad(env2, true);
		newTetrad();
		display.showBlocks();
		rowsMoved=1;
		
		paused = !runLater;

		if (!runLater)
		{
			while(notLost())
			{
				play();
			}
			rad=null;
			frame.setTitle("Tetris! Level: "+level+" Score: "+score+" Game Over!");
		}
	}

	public JPanel getPanel()
	{
		return panel;
	}
	public int getLevel()
	{
		return level;
	}
	public int getScore()
	{
		return score;
	}
	public void printStats(String prepend)
	{
		System.out.println(prepend + " Level: " + level);
		System.out.println(prepend + " Score: " + score);
		System.out.println(prepend + " Singles: " + stats[0]);
		System.out.println(prepend + " Doubles: " + stats[1]);
		System.out.println(prepend + " Triples: " + stats[2]);
		System.out.println(prepend + " Tetrises: " + stats[3]);
		System.out.println(prepend + " Rows Sent: " + stats[4]);
	}

	public void setAI(TetrisAI ai)
	{
		this.ai = ai;
	}
	public MyBoundedEnv board()
	{
		return env;
	}
	public Tetrad currentRad()
	{
		return rad;
	}
	public Tetrad nextRad()
	{
		return rad2;
	}
	public void setNextRad(Tetrad newRad)
	{
		rad2 = newRad;
	}

	public void setOpponent(Tetris other)
	{
		opp = other;
	}
	public void increasePendingRows(int num)
	{
		pendingRows += num;
	}
	public void resetPendingRows()
	{
		pendingRows = 0;
	}
	public void addPendingRows()
	{
		if (pendingRows == 0)
			return;
		// First move every block on the board up by 'pendingRows'
		// Obviously if some go negative y index you've just got to remove them.
		// Fill the remainder with grayish blocks excluding 1 block per row (at random)

		for (int i = 0; i < env.numRows(); i++)
		{
			for (int j = 0; j < env.numCols(); j++)
			{
				Block a = (Block)env.objectAt(new Location(i, j));
				if (a != null)
				{
					env.remove(a);

					if (i >= pendingRows)
					{
						((Block)a).setLocation(new Location(i-pendingRows,j));
						env.recordMove(a,new Location(i-pendingRows,j));
					}
				}
			}
		}

		for (int i = env.numRows() - pendingRows; i < env.numRows(); i++)
		{
			int exclude = (int)(Math.random()*env.numCols());
			int alsoExclude = -1;

			if (Math.random() < .25)
				alsoExclude = (int)(Math.random()*env.numCols());
			for (int j = 0; j < env.numCols(); j++)
			{
				if (j != exclude && j != alsoExclude)
				{
					Location a = new Location(i, j);
					Block b = new Block(Color.BLACK);
					b.setLocation(a);
					env.add(b);
				}
			}
		}

		// added the rows, so we're done
		resetPendingRows();

	}
	public PowerUp setCurrentPowerUp(int powerType)
	{
		if (currentPowerUp == null) // to avoid conflicts
		{
			currentPowerUp = new PowerUp(this, env, env2, powerType);
			return currentPowerUp;
		}
		return null;
	}

	public boolean notLost()
	{
		if(rowsMoved!=-1)
			return true;
		frame.setTitle("Tetris! Level: "+level+" Score: "+score+" Game Over!");
		return false;
	}
	public void restart()
	{
		env.clearAll();
		env2.clearAll();
		rad = null;
		rad2=new Tetrad(env2, true);
		rowsMoved = 0;
		level = 1;
		score = 0;
		stats = new int[5];
		newTetrad();
		display.showBlocks();
		display2.showBlocks();

		currentPowerUp = null;
		pendingRows = 0;
	}

	public boolean newTetrad()
	{
		if (rad != null)
			rad.activate();
		Tetrad oldRad = rad;

		rad=rad2.changeEnv(env); // oh dear what if I press down right now?! Then overlap => premature gameover

		rad.activate();

		env2.clearAll();
		display2.showBlocks();
		rad2=new Tetrad(env2, oldRad);
		Locatable[] b=rad2.blocks();
		for(int i=0;i<b.length;i++)
		{
			env2.add(b[i]);
		}
		display2.showBlocks();
		boolean c=true;
		Locatable[] a=rad.blocks();
		for(int i=0;i<a.length;i++)
		{
			if(env.isEmpty(a[i].location()))
				env.add(a[i]);
			else
				c=false;
		}
		return c;
	}
	
	
	public void leftPressed()
	{
		if(rad!=null&&rad.translate(0,-1))
			display.showBlocks();
	}
	public void rightPressed()
	{
		if(rad!=null&&rad.translate(0,1))
			display.showBlocks();
	}
	public void upPressed()
	{
		if(rad!=null&&rad.rotate())
			display.showBlocks();
	}
	public void downEnd()
	{
		arrows[3] = false;
	}

	public void downStart()
	{
		arrows[3] = true;
		if (opp == null)
			downPressed();
	}
	public void downPressed()
	{
		if(rad!=null)
		{
		if(rad.translate(1,0))
		{
			display.showBlocks();
			rowsMoved++;
			score+=level;
			if(score-(level*level*100)>0)
				increaseLevel();
		}
		}
	}
	public void pPressed()
	{
		paused=!paused;
		if(paused)
			frame.setTitle("Tetris! Level: "+level+" Score: "+score+" Paused!");
		else
			frame.setTitle("Tetris! Level: "+level+" Score: "+score+" Resuming...");
	}
	public void spacePressed()
	{
		if(rad.blocks()[0].color()==Color.black)
		{
			blowUp();
			boolean a=newTetrad();
			display.showBlocks();
			frame.setTitle("Tetris! Level: "+level+" Score: "+score+" Kaboom!");
			if(!a)
			{
				frame.setTitle("Tetris! Level: "+level+" Score: "+score+" Game Over!");
				rowsMoved=-10;
			}
		}
		if(rad.blocks()[0].color()==Color.white)
		{
			blowDown();
			display.showBlocks();
			clearCompletedRows();
			boolean a=newTetrad();
			display.showBlocks();
			frame.setTitle("Tetris! Level: "+level+" Score: "+score+" Pop!");
			if(!a)
			{
				frame.setTitle("Tetris! Level: "+level+" Score: "+score+" Game Over!");
				rowsMoved=-10;
			}
		}
	}	
	public void qPressed() { }
	public void ePressed() { }
	public void sStart() { }
	public void sEnd() { }
	public void periodPressed() { }
	public void commaPressed() { }
	public void enterPressed() { }
	public void wPressed() { }
	public void sPressed() { }
	public void aPressed() { }
	public void dPressed() { }
	
	
	public void nullRad()
	{
		rad=null;
	}
	// moves the arrows
	public void act()
	{
		if (arrows[3])
			downPressed();

		if (ai != null)
		{
			ai.think();
			ai.actuate();
		}
		display.showBlocks();
	}
	public void play()
	{
		try
		{
			if (opp == null)
			{
				Thread.sleep(1000-level*10);
				while(paused)
					Thread.sleep(1000);//1000 milliseconds
			}
			display.showBlocks();
			if(rad.moveDown())
			{
				display.showBlocks();
				rowsMoved++;
			}
			else
			{
				if (currentPowerUp != null && currentPowerUp.expiring())
					currentPowerUp.afterAction();

				if (currentPowerUp != null && !currentPowerUp.expiring())
					currentPowerUp.increaseTime();
				else
					currentPowerUp = null;
				clearCompletedRows();
				display.showBlocks();


				addPendingRows();

				rowsMoved=0;
				downEnd();

				if (currentPowerUp != null)
					currentPowerUp.beforeAction();

				display.showBlocks();
				boolean a=newTetrad();
				if(!a)
				{
					frame.setTitle("Tetris! Level: "+level+" Score: "+score+" Game Over!");
					rowsMoved=-1;
				}
				display.showBlocks();
				score+=5*level;
				if(score-(level*level*1000)>0)
					increaseLevel();
				frame.setTitle("Tetris! Level: "+level+" Score: "+score);
			}
		}
		catch(InterruptedException e)
		{
			//ignore this blank space
		}
	}
	private void blowUp()
	{
		Block a=rad.blocks()[0];
		Location loc=a.location();
		int row=loc.row();
		int col=loc.col();
		for (int i = row - 1; i <= row+1; i++)
		{
			for (int j = col - 1; j <= col+1; j++)
			{
				Location l = new Location(i, j);
				Locatable lb = env.objectAt(l);
				if (lb != null)
				{
					env.remove(lb);
					score+=level*5;
				}
			}
		}
		display.showBlocks();
		frame.setTitle("Tetris! Level: "+level+" Score: "+score);
	}
	private void blowDown()
	{
		Block a=rad.blocks()[0];
		Location loc=a.location();
		int row=loc.row();
		int col=loc.col();
		for (int i = row - 1; i <= row+1; i++)
		{
			for (int j = col - 1; j <= col+1; j++)
			{
				Location l = new Location(i, j);
				if (env.isValid(l) && env.isEmpty(l))
				{
					Block b=new Block(Color.white);
					b.setLocation(l);
					env.add(b);
					score+=level*5;
				}
			}
		}
		display.showBlocks();
		frame.setTitle("Tetris! Level: "+level+" Score: "+score);
	}
	private boolean isCompletedRow(int row)
	{
		int a=0;
		int col=env.numCols();
		for(int j=0;j<col;j++)
		{
			if(!env.isEmpty(new Location(row,j)))
				a++;
		}
		if(a==col)
			return true;
		return false;
	}
	private int clearRow(int row)
	{
		int powerUp = 0;
		for(int i=0;i<env.numCols();i++)
		{
			Block b = (Block)env.objectAt(new Location(row,i));
			if (powerUp == 0)
				powerUp = b.getPowerType();
			env.remove(b);
		}
		for(int row2=row;row2>=0;row2--)
		{
			for(int i=0;i<env.numCols();i++)
			{
				Locatable a=env.objectAt(new Location(row2,i));
				if(a!=null)
				{
					env.remove(a);
					((Block)a).setLocation(new Location(row2+1,i));
					env.recordMove(a,new Location(row2+1,i));
				}
			}
		}

		return powerUp;
	}
	public void clearCompletedRows()
	{
		int a=0;
		for(int i=0;i<env.numRows();i++)
		{
			if(isCompletedRow(i))
			{
				int powerType = clearRow(i);
				if (powerType != PowerUp.POWERUP_NORMAL)
				{
					if (PowerUp.mineOrOpp(powerType))
					{
						setCurrentPowerUp(powerType);
						currentPowerUp.changeRad();
					}
					else
					{
						if (opp != null)
						{
							PowerUp p = opp.setCurrentPowerUp(powerType);
							if (p != null)
								p.changeRad();
						}
					}
				}

				a++;
			}
			for (int j = 0; j < env.numCols();j++)
			{
				Block b = (Block)env.objectAt(new Location(i, j));
				if (b != null)
					b.setPowerType(PowerUp.POWERUP_NORMAL);
			}
		}
		if(a==1)
		{
			score+=40*level;
			stats[0]++;
		}
		if(a==2)
		{
			score+=100*level;
			stats[1]++;
		}
		if(a==3)
		{
			score+=300*level;
			stats[2]++;
		}
		if(a==4)
		{
			score+=1200*level;
			stats[3]++;
		}
		while(score-(level*level*100)>0)
			increaseLevel();
		frame.setTitle("Tetris! Level: "+level+" Score: "+score);

		// Penalize opponent!
		if (opp != null && a > 1)
		{
			opp.increasePendingRows(a - 1);
			stats[4]+=a-1;
		}
	}
	public void increaseLevel()
	{
		level++;
		if (opp != null && level % 4 == 0)
		{
			opp.increasePendingRows(1);
			stats[4]++;
		}
	}

	public void setLocationEnvTop(int x,int y)
	{
		frame.setLocation(x, y);
	}
	public void setLocationEnvBottom(int x,int y)
	{
		// Do nothing
	}

}