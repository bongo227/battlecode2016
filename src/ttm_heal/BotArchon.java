package ttm_heal;


import battlecode.common.*;

public class BotArchon extends Globals {
	
	
	public static void loop() throws GameActionException {
		FastMath.initRand(rc);
		initArchons();
		while (true) {
			try {
				numTurns += 1;
				Globals.update();
			    turn();
			} catch (Exception e) {
				e.printStackTrace();
			}
			Clock.yield();
		}
	}
	
	public static int numTurns = 0;
	
	private static void turn() throws GameActionException {
		avoidEnemy();
		countTurret();
		exploreForNeutralsAndParts();
		trySpawn();
		tryRepairAlly();
		tryConvertNeutrals();
	}
	
	public static int nArchons = 0;
	public static MapLocation[] archonsLoc = new MapLocation[10];
	public static int[] archonsId = new int[10];
	public static int archonOrder = 0;
	
	public static MapLocation rallyPoint = null;
	
	public static void initArchons() throws GameActionException {
		Globals.update();
		archonsLoc[nArchons] = here;
		archonsId[nArchons] = myID;
		nArchons += 1;
		Messages.sendMapLocation(Messages.SOURCE_MASK, here, MapEdges.maxBroadcastDistSq());
		Clock.yield();
		numTurns += 1;
		Signal[] signals = rc.emptySignalQueue();
		for (Signal sig : signals) {
			if (sig.getTeam() != us) continue;
			int[] data = sig.getMessage();
			if (data != null) {
				if (data[0] == Messages.SOURCE_MASK) {
					archonsLoc[nArchons] = sig.getLocation();
					archonsId[nArchons] = sig.getID();
					nArchons += 1;
				}
			}
		}
		rallyPoint = here;
		for (int i = 1; i < nArchons; ++i) {
			rallyPoint = FastMath.addVec(rallyPoint, archonsLoc[i]);
		}
		rallyPoint = FastMath.multiplyVec(1.0/(double)nArchons, rallyPoint);
		rallyPoint = rallyPoint.add(rallyPoint.directionTo(here), 0);
		rc.setIndicatorDot(rallyPoint, 255, 0, 0);
		for (int i = 0; i < nArchons; ++i) {
			if (archonsId[i] < myID) {
				archonOrder += 1;
			}
		}
	}
	
	private static void exploreForNeutralsAndParts() throws GameActionException {
		if (!rc.isCoreReady()) return;
		
		MapLocation[] nearbyLocs = MapLocation.getAllMapLocationsWithinRadiusSq(here, RobotType.ARCHON.sensorRadiusSquared);
		
		MapLocation bestLoc = null;
		int bestDistSq = Integer.MAX_VALUE;
		for (MapLocation loc : nearbyLocs) {
			double parts = rc.senseParts(loc);
			if (parts == 0) {
				RobotInfo info = rc.senseRobotAtLocation(loc);
				if (info == null || info.team != Team.NEUTRAL) continue;
			}
			// there are either parts or a neutral at this location
			int distSq = here.distanceSquaredTo(loc);
			if (distSq < bestDistSq) {
				bestDistSq = distSq;
				bestLoc = loc;
			}			
		}
		if (nTurret >= nTurretMax) {
//			int rdn = FastMath.rand256();
//			Direction dir = Direction.values()[rdn % 8];
//			if (Bug.tryMoveInDirection(dir)) {
//				return;
//			}
		} else {
//			Direction dir = saferDir();
//			if (dir != null) {
//				rc.move(dir);
//				return;
//			}
		}
//		if (bestLoc != null) {
//			DBug.goTo(bestLoc);
//		}
		if (numTurns <= 60 && rallyPoint != null && here.distanceSquaredTo(rallyPoint) > 8) {
			DBug.goTo(rallyPoint);
		}
	}
	
	private static void avoidEnemy() throws GameActionException {
		if (!rc.isCoreReady()) return;
		Direction escapeDir = avoidEnemyDirection();
		if (null != escapeDir) {
//			System.out.println("Try escape!");
			if (Bug.tryMoveInDirection(escapeDir)) {
//				System.out.println("escape!");
				return;
			}
		}
	}
	
	private static Direction avoidEnemyDirection() {
		Direction escapeDir = null;
		if (escapeDir == null) {
			RobotInfo[] enemies = rc.senseNearbyRobots(mySensorRadiusSquared, Team.ZOMBIE);
			for (RobotInfo e : enemies) {
//				System.out.println("Got zombie in sight!");
				if (e.location.distanceSquaredTo(here) <= e.type.attackRadiusSquared) {
//					System.out.println("Got zombie in range!");
					escapeDir = e.location.directionTo(here);
				}
			}
		}
		if (escapeDir == null) {
			RobotInfo[] enemies = rc.senseNearbyRobots(mySensorRadiusSquared, them);
			for (RobotInfo e : enemies) {
//				System.out.println("Got enemy in sight!");
				if (e.location.distanceSquaredTo(here) <= e.type.attackRadiusSquared) {
//					System.out.println("Got enemy in range!");
					escapeDir = e.location.directionTo(here);
				}
			}
		}
		return escapeDir;
	}
	
	private static int spawnCount = 0;

	private static int nTurret = 0;
	private static int nTurretMax = 4;
	
	private static void countTurret() throws GameActionException {
		nTurret = 0;
		RobotInfo[] infos = rc.senseNearbyRobots(2, us);
		for (RobotInfo info : infos) {
			if (info.type == RobotType.TURRET) {
				nTurret += 1;
			}
		}
		nTurretMax = nTurret;
		for (Direction dir : Direction.values()) {
			MapLocation tl = here.add(dir);
			if (0 == (tl.x + tl.y) % 2 && rc.canMove(dir)) {
				nTurretMax += 1;
			}
		}
	}
	
	private static Direction saferDir() throws GameActionException {
		Direction bestDir = null;
		int bestCount = nTurret; // need to count Turret first
		RobotInfo[] infos = rc.senseNearbyRobots(9, us);
		for (Direction dir : Direction.values()) {
			if (!rc.canMove(dir)) {
				continue;
			}
			MapLocation lc = here.add(dir);
			int count = 0;
			for (RobotInfo info : infos) {
				if (info.type == RobotType.TURRET && info.location.distanceSquaredTo(lc) <= 2) {
					count += 1;
				}
			}
			if (count > bestCount) {
				bestCount = count;
				bestDir = dir;
			}
		}
		return bestDir;
	}
	
	private static void trySpawn() throws GameActionException {
		if (!rc.isCoreReady()) return;
		if (null != saferDir()) return;
		
		rc.setIndicatorString(2, "trySpawn: turn " + rc.getRoundNum());
		
		RobotType spawnType = ((spawnCount - archonOrder - 2) % 5 == 0 ? RobotType.SCOUT : RobotType.TURRET);

		if (!rc.hasBuildRequirements(spawnType)) return;

		// scouts can probably have different spawning conditions
		double parts = rc.getTeamParts();
		if (nTurret <= 1 || parts > 180 || parts > FastMath.rand256()) {
			for (Direction dir : Direction.values()) {
				MapLocation tl = here.add(dir);
				if (0 == (tl.x + tl.y) % 2 && rc.canBuild(dir, spawnType)) {
					rc.build(dir, spawnType);
					++spawnCount;
					return;
				}
			}
			for (Direction dir : Direction.values()) {
				if (rc.canBuild(dir, spawnType)) {
					rc.build(dir, spawnType);
					++spawnCount;
					return;
				}
			}
		}
	}
	
	private static void tryRepairAlly() throws GameActionException {
		RobotInfo[] healableAllies = rc.senseNearbyRobots(RobotType.ARCHON.attackRadiusSquared, us);
		MapLocation bestLoc = null;
		double lowestHealth = 10000;
		for (RobotInfo ally : healableAllies) {
			if (ally.type == RobotType.ARCHON) continue;
			if (ally.health < ally.maxHealth && ally.health < lowestHealth) {
				bestLoc = ally.location;
				lowestHealth = ally.health;
			}
		}
		if (bestLoc != null) {
			rc.setIndicatorDot(bestLoc, 255, 0, 0);
			rc.repair(bestLoc);
		}
	}
	
	private static void tryConvertNeutrals() throws GameActionException {
		if (!rc.isCoreReady()) return;
		RobotInfo[] adjacentNeutrals = rc.senseNearbyRobots(2, Team.NEUTRAL);
		for (RobotInfo neutral : adjacentNeutrals) {
			rc.activate(neutral.location);
			return;
		}
	}
}
