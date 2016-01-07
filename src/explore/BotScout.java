package explore;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Signal;
import battlecode.common.Team;

public class BotScout extends Globals {
	private static MapLocationHashSet knownZombieDens = new MapLocationHashSet();
	private static MapLocationHashSet knownPartLocations = new MapLocationHashSet();
	private static MapLocation origin;
	private static boolean[][] exploredGrid = new boolean[100][100];
	private static MapLocation exploreDest = null;
	private static boolean finishedExploring = false;
	private static final int gridSpacing = 10;
	
	public static void loop() {
    	Debug.init("explore");
    	origin = here;
    	exploredGrid[50][50] = true;    	
		while (true) {
			try {
				Globals.update();
			    turn();
			} catch (Exception e) {
				e.printStackTrace();
			}
			Clock.yield();
		}
	}
	
	private static void trySendTurretTarget() throws GameActionException {
		RobotInfo[] allies = rc.senseNearbyRobots(mySensorRadiusSquared, us);
		MapLocation[] turrets = new MapLocation[1000];
		int numTurrets = 0;
		for (RobotInfo ally : allies) {
			if (ally.type == RobotType.TURRET || ally.type == RobotType.TTM) {
				turrets[numTurrets++] = ally.location;
			}
		}
		
		RobotInfo[] enemies = rc.senseNearbyRobots(mySensorRadiusSquared, them);
		RobotInfo[] zombies = rc.senseNearbyRobots(mySensorRadiusSquared, Team.ZOMBIE);
		MapLocation bestTarget = null;
		int bestScore = Integer.MIN_VALUE;
		for (RobotInfo enemy : enemies) {
			int score = 0;
			MapLocation enemyLoc = enemy.location;
			for (int i = 0; i < numTurrets; ++i) {
				int distSq = enemyLoc.distanceSquaredTo(turrets[i]);
				if (distSq > RobotType.TURRET.sensorRadiusSquared && distSq <= RobotType.TURRET.attackRadiusSquared) {
					++score;
				}
			}
			if (score > bestScore) {
				bestScore = score;
				bestTarget = enemyLoc;
			}
		}
		for (RobotInfo enemy : zombies) {
			int score = 0;
			MapLocation enemyLoc = enemy.location;
			for (int i = 0; i < numTurrets; ++i) {
				int distSq = enemyLoc.distanceSquaredTo(turrets[i]);
				if (distSq > RobotType.TURRET.sensorRadiusSquared && distSq <= RobotType.TURRET.attackRadiusSquared) {
					++score;
				}
			}
			if (score > bestScore) {
				bestScore = score;
				bestTarget = enemyLoc;
			}
		}
		
		if (bestTarget != null) {
			Debug.indicate("spotting", 0, "spotting target at " + bestTarget);
			Messages.sendTurretTarget(bestTarget, 2*mySensorRadiusSquared);
		}
	}
	
	private static void trySendZombieDenLocation() throws GameActionException {
		RobotInfo[] zombies = rc.senseNearbyRobots(mySensorRadiusSquared, Team.ZOMBIE);
		for (RobotInfo zombie : zombies) {
			if (zombie.type == RobotType.ZOMBIEDEN) {
				MapLocation denLoc = zombie.location;
				if (knownZombieDens.add(zombie.location)) {
					Messages.sendZombieDenLocation(denLoc, MapEdges.maxBroadcastDistSq());
				}
			}
		}
	}
	

	private static void processSignals() {
		Signal[] signals = rc.emptySignalQueue();
		for (Signal sig : signals) {
			if (sig.getTeam() != us) continue;
			
			int[] data = sig.getMessage();
			if (data != null) {
				switch(data[0] & Messages.CHANNEL_MASK) {
				case Messages.CHANNEL_ZOMBIE_DEN:
					knownZombieDens.add(Messages.parseZombieDenLocation(data));
					break;
				case Messages.CHANNEL_FOUND_PARTS:
					knownPartLocations.add(Messages.parsePartsLocation(data).location);
					break;
				
				case Messages.CHANNEL_MAP_MIN_X:
					Messages.processMapMinX(data);
					break;
				case Messages.CHANNEL_MAP_MAX_X:
					Messages.processMapMaxX(data);
					break;
				case Messages.CHANNEL_MAP_MIN_Y:
					Messages.processMapMinY(data);
					break;
				case Messages.CHANNEL_MAP_MAX_Y:
					Messages.processMapMaxY(data);
					break;
					
				default:
				}
			}
		}
	}
	
	
	
	private static MapLocation gridLocation(int gridX, int gridY) {
		return new MapLocation(origin.x + gridSpacing*(gridX-50), origin.y + gridSpacing*(gridY-50));
	}
	
	private static int nearestGridX(MapLocation loc) {
		return 50 + (int)Math.round((here.x - origin.x)/((double)gridSpacing));
	}
	
	private static int nearestGridY(MapLocation loc) {
		return 50 + (int)Math.round((here.y - origin.y)/((double)gridSpacing));
	}
	
	private static int[] legDX = { 0, 0, 0, -1, 0, 0, 0, 1 };
    private static int[] legDY = { 0, 1, 0, 0, 0, -1, 0, 0 };
    
    private static boolean isFarOffMap(MapLocation loc) {
    	return (MapEdges.minX != MapEdges.UNKNOWN && loc.x < MapEdges.minX - gridSpacing/2)
				|| (MapEdges.maxX != MapEdges.UNKNOWN && loc.x > MapEdges.maxX + gridSpacing/2) 
				|| (MapEdges.minY != MapEdges.UNKNOWN && loc.y < MapEdges.minY - gridSpacing/2) 
				|| (MapEdges.maxY != MapEdges.UNKNOWN && loc.y > MapEdges.maxY + gridSpacing/2);
    }
    
    private static MapLocation moveOntoMap(MapLocation loc) {
    	int retX = loc.x;
    	int retY = loc.y;
    	if (MapEdges.minX != MapEdges.UNKNOWN && retX < MapEdges.minX) {
    		retX = MapEdges.minX;
    	}
    	if (MapEdges.maxX != MapEdges.UNKNOWN && retX > MapEdges.maxX) {
    		retX = MapEdges.maxX;
    	}
    	if (MapEdges.minY != MapEdges.UNKNOWN && retY < MapEdges.minY) {
    		retY = MapEdges.minY;
    	}
    	if (MapEdges.maxY != MapEdges.UNKNOWN && retY > MapEdges.maxY) {
    		retY = MapEdges.maxY;
    	}
    	return new MapLocation(retX, retY);
    }
    
	private static void pickNewExploreDest() {
		int centerX = nearestGridX(here);
		int centerY = nearestGridY(here);
		if (!exploredGrid[centerX][centerY]) {
			MapLocation centerLoc = gridLocation(centerX, centerY);
			if (!isFarOffMap(centerLoc)) {
				exploreDest = moveOntoMap(centerLoc);
				return;
			}
		}
		
        Direction startDiag = here.directionTo(origin);
        if (startDiag == Direction.NONE || startDiag == Direction.OMNI) startDiag = Direction.NORTH_EAST;
        if (!startDiag.isDiagonal()) startDiag = startDiag.rotateLeft();
        
		for (int radius = 1; radius <= 10; radius++) {
            MapLocation bestLoc = null;
            int bestDistSq = 999999;

            int gridX = centerX + radius * startDiag.dx;
            int gridY = centerY + radius * startDiag.dy;
            int diag = startDiag.ordinal();
            for (int leg = 0; leg < 4; leg++) {
                for (int i = 0; i < 2 * radius; i++) {
                	if (!exploredGrid[gridX][gridY]) {
            			MapLocation gridLoc = gridLocation(gridX, gridY);
            			if (!isFarOffMap(gridLoc)) {
            				gridLoc = moveOntoMap(gridLoc);
            				rc.setIndicatorDot(gridLoc, 0, 0, 255);
            				int distSq = origin.distanceSquaredTo(gridLoc);
                            if (distSq < bestDistSq) {
                                bestDistSq = distSq;
                                bestLoc = gridLoc;
                            }
                        }
                    }

                    gridX += legDX[diag];
                    gridY += legDY[diag];
                }

                diag = (diag + 2) % 8;
            }

            if (bestLoc != null) {
            	exploreDest = bestLoc;
				rc.setIndicatorDot(exploreDest, 0, 255, 0);
				return;
            }
        }
		
		exploreDest = null;
		finishedExploring = true;
	}
	
	private static void explore() throws GameActionException {
		if (finishedExploring) return;
		
		Debug.indicate("explore", 0, "exploreDest = " + exploreDest);
			
		if (exploreDest != null && 
				(here.equals(exploreDest) || 
				here.isAdjacentTo(exploreDest) && rc.senseRobotAtLocation(exploreDest) != null)) {
			// we reached the exploreDest
			int gridX = nearestGridX(here);
			int gridY = nearestGridY(here);
			exploredGrid[gridX][gridY] = true;
			exploreDest = null; // pick a new explore dest
		}
		
		if (exploreDest == null || MapEdges.isOffMap(exploreDest)) {
			pickNewExploreDest();
			Debug.indicate("explore", 1, "picked new exploreDest = " + exploreDest);
			return;
		}

		
		rc.setIndicatorDot(exploreDest, 0, 255, 0);
		if (rc.isCoreReady()) {
			Debug.indicate("explore", 1, "going to exploreDest");
			Bug.goTo(exploreDest);
		}
	}
	
	private static void turn() throws GameActionException {
		processSignals();		
		MapEdges.detectAndBroadcastMapEdges(7); // visionRange = 7
		Debug.indicate("edges", 0, String.format("map X = [%d, %d], mapY = [%d, %d]", MapEdges.minX, MapEdges.maxX, MapEdges.minY, MapEdges.maxY));

		trySendZombieDenLocation();
		
		
		explore();
		
		/*if (rc.getRoundNum() % 100 == 0) {
			Messages.sendZombieDenLocation(new MapLocation(0,0), 100*100);
		}*/
	}
}
