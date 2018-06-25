package se1.schiffeVersenken.ais.ReferenceAi;

import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import se1.schiffeVersenken.botBattle.util.Grid2;
import se1.schiffeVersenken.interfaces.*;
import se1.schiffeVersenken.interfaces.exception.action.InvalidActionException;
import se1.schiffeVersenken.interfaces.exception.shipPlacement.InvalidShipPlacementException;
import se1.schiffeVersenken.interfaces.util.Direction;
import se1.schiffeVersenken.interfaces.util.Position;

import static se1.schiffeVersenken.interfaces.Tile.*;

@PlayableAI("Reference Player")
public class ReferencePlayerCreator implements PlayerCreator {
	
	private static final int TRIES_COMPLETE_REDO = 100000;
	private static final int TRIES_REPLACE_SHIP = 200;
	private static final int NEXT_TO_SHIP_IMPORTANCE = 1024;
	
	private final AtomicInteger ID_COUNTER = new AtomicInteger();
	private final Supplier<Random> R_CREATOR;
	
	//allow print
	private boolean printShipConfig = false;
	private boolean allowTalking = false;
	
	//constructor
	public ReferencePlayerCreator() {
		this(Random::new);
	}
	
	public ReferencePlayerCreator(int seed) {
		this(() -> new Random(seed));
	}
	
	public ReferencePlayerCreator(Supplier<Random> R_CREATOR) {
		this.R_CREATOR = R_CREATOR;
	}
	
	//allow print
	public ReferencePlayerCreator setPrintShipConfig(boolean printShipConfig) {
		this.printShipConfig = printShipConfig;
		return this;
	}
	
	public ReferencePlayerCreator setAllowTalking(boolean allowTalking) {
		this.allowTalking = allowTalking;
		return this;
	}
	
	//player
	@Override
	public Player createPlayer(GameSettings settings, Class<? extends PlayerCreator> otherPlayer) {
		return new ReferencePlayer(settings, createImportanceFunctionFromArray(new float[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}));
	}
	
	private class ReferencePlayer implements Player {
		
		private final int id = ID_COUNTER.getAndIncrement();
		private final Random r = R_CREATOR.get();
		private final GameSettings settings;
		private final ImportanceFunction importanceFunction;
//		private final int[] lengthToShipPossibleCount;
		
		private Ship[] ships;
		private Grid2<Tile> shots = new Grid2<>(GameSettings.SIZE_OF_PLAYFIELD_VECTOR);
//		private int shotCount;
		
		
		public ReferencePlayer(GameSettings settings, ImportanceFunction importanceFunction) {
			this.settings = settings;
			this.importanceFunction = importanceFunction;
		}

//		public ReferencePlayer(GameSettings settings) {
//			this.settings = settings;

//			int[] numberOfShips = settings.getNumberOfShips();
//			this.lengthToShipPossibleCount = new int[numberOfShips.length];
//			for (int i = 0; i < numberOfShips.length; i++)
//				for (int j = 0; j <= i; j++)
//					this.lengthToShipPossibleCount[i] += numberOfShips[j] * (i - j + 1);
//		}
		
		/**
		 * Warning: nested for loops ahead!!!
		 */
		@Override
		public void placeShips(ShipPlacer placer) {
			int[] numberOfShips = settings.getNumberOfShips();
			int totalShipCount = IntStream.of(numberOfShips).sum();
			
			if (printShipConfig)
				println("Placing ships...");
			labelCompleteRetry:
			for (int completeTry = 0; true; completeTry++) {
				if (!(completeTry < TRIES_COMPLETE_REDO))
					throw new RuntimeException("Out of tries!");
				
				int shipArrayIndex = 0;
				ships = new Ship[totalShipCount];
				for (int shipLength = numberOfShips.length; shipLength > 0; shipLength--) {
					int shipCount = numberOfShips[shipLength - 1];
					
					for (int i = 0; i < shipCount; i++) {
						for (int replaceTry = 0; true; replaceTry++) {
							if (!(replaceTry < TRIES_REPLACE_SHIP))
								continue labelCompleteRetry;
							
							Direction direction = r.nextBoolean() ? Direction.HORIZONTAL : Direction.VERTICAL;
							Position position = new Position(
									r.nextInt(GameSettings.SIZE_OF_PLAYFIELD - ((direction == Direction.HORIZONTAL) ? shipLength : 1)),
									r.nextInt(GameSettings.SIZE_OF_PLAYFIELD - ((direction == Direction.VERTICAL) ? shipLength : 1)));
							ships[shipArrayIndex] = new Ship(position, direction, shipLength);
							
							try {
								ShipWorldImplChanged.create(settings, Arrays.copyOf(ships, shipArrayIndex + 1));
								shipArrayIndex++;
								break;
							} catch (InvalidShipPlacementException ignore) {
								//the new ship doesn't work, retrying
							}
						}
					}
				}
				break;
			}
			
			if (printShipConfig) {
				println("All ships:");
				Stream.of(ships).forEach(s -> println(Objects.toString(s)));
				println();
			}
			
			try {
				placer.setShips(ships);
			} catch (InvalidShipPlacementException e) {
				throw new RuntimeException("Should not happen!", e);
			}
		}
		
		@Override
		public void takeTurn(TurnAction turnAction) {
//			shotCount++;
//			if (shotCount > GameSettings.SIZE_OF_PLAYFIELD * GameSettings.SIZE_OF_PLAYFIELD)
//				throw new RuntimeException("Should not happen");
//
//			while (true) {
//				try {
//					Position position = new Position(r.nextInt(GameSettings.SIZE_OF_PLAYFIELD), r.nextInt(GameSettings.SIZE_OF_PLAYFIELD));
//					Tile tile = turnAction.shootTile(position);
//					if (allowTalking)
//						println(position + " -> " + tile);
//					break;
//				} catch (AlreadyShotPositionException ignore) {
//					//continue
//				} catch (InvalidActionException e) {
//					throw new RuntimeException(e);
//				}
//			}
			
			Grid2<Integer> importanceGrid = new Grid2<>(GameSettings.SIZE_OF_PLAYFIELD_VECTOR, 0);
			for (int x = 0; x < GameSettings.SIZE_OF_PLAYFIELD; x++) {
				for (int y = 0; y < GameSettings.SIZE_OF_PLAYFIELD; y++) {
					Position pos = new Position(x, y);
					Tile tile = shots.get(pos);
					
					//space for ship
					if (tile == null) { //already shot at -> skip
						int importance = 0;
						for (Direction direction : new Direction[]{Direction.VERTICAL, Direction.HORIZONTAL}) {
							int lengthPos = getDirectionalMaxLength(pos, direction.positive);
							int lengthNeg = getDirectionalMaxLength(pos, direction.negative);
							int lengthTotal = lengthPos + lengthNeg + 1;
							
							for (int shipLengthId = 0; shipLengthId < lengthTotal; shipLengthId++) {
								int mostNeg = Integer.min(lengthNeg, shipLengthId);
								int mostPos = Integer.min(lengthPos, shipLengthId);
								importance += importanceFunction.importance(shipLengthId, (mostPos + mostNeg + 1), settings.getNumberOfShips(shipLengthId + 1));
							}
						}
						final int importance1 = importance;
						importanceGrid.accumulate(pos, i -> i + importance1);
					}
					
					//ship next to
					boolean isShipKill = tile == SHIP_KILL;
					if (tile == SHIP || isShipKill) {
						for (Direction direction : new Direction[]{Direction.VERTICAL, Direction.HORIZONTAL}) {
							for (Position dirPos : new Position[]{direction.positive, direction.negative}) {
								Position checkPos = pos.add(dirPos);
								if (!checkPos.boundsCheck(Position.NULL_VECTOR, GameSettings.SIZE_OF_PLAYFIELD_VECTOR))
									break;
								importanceGrid.accumulate(checkPos, i -> i + NEXT_TO_SHIP_IMPORTANCE);
							}
						}
					}
				}
			}
			
			//get Position with max importance
			int mostLikelyImportance = 0;
			Position mostLikely = null;
			for (int x = 0; x < GameSettings.SIZE_OF_PLAYFIELD; x++) {
				for (int y = 0; y < GameSettings.SIZE_OF_PLAYFIELD; y++) {
					Position pos = new Position(x, y);
					if (shots.get(pos) != null) //already shot at -> skip
						continue;
					
					int importance = importanceGrid.get(pos);
					if (importance > mostLikelyImportance) {
						mostLikelyImportance = importance;
						mostLikely = pos;
					}
				}
			}
			
			//shoot the Tile
			if (mostLikely == null)
				throw new RuntimeException("No tile to shoot!");
			try {
				switch (turnAction.shootTile(mostLikely)) {
					case WATER:
						shots.set(mostLikely, WATER);
						break;
					case SHIP:
						shots.set(mostLikely, SHIP);
						break;
					case SHIP_KILL:
						shots.set(mostLikely, SHIP_KILL);
						break;
					default:
						throw new RuntimeException("Invalid tile response");
				}
			} catch (InvalidActionException e) {
				throw new RuntimeException(e);
			}
		}
		
		private int getDirectionalMaxLength(Position relative, Position direction) {
			int ret = 0;
			for (int i = 1; true; i++) {
				Position checkPos = relative.add(direction.multiply(i));
				if (!checkPos.boundsCheck(Position.NULL_VECTOR, GameSettings.SIZE_OF_PLAYFIELD_VECTOR))
					break;
				if (shots.get(checkPos) != null) //already shot at -> break
					break;
				ret++;
			}
			return ret;
		}
		
		@Override
		public void onEnemyShot(Position position, Tile tile, Ship ship) {
			if (allowTalking) {
				if (ship != null)
					println("Ship id " + indexOf(ships, ship) + (ship.isSunk() ? " got sunk!" : " got hit: " + ship.getHealth() + " HP left!"));
			}
		}
		
		private <T> int indexOf(T[] array, T obj) {
			for (int i = 0; i < array.length; i++)
				if (array[i] == obj)
					return i;
			return -1;
		}
		
		@Override
		public void gameOver(boolean youHaveWon) {
			if (allowTalking) {
				if (youHaveWon)
					println("Winner Winner Chicken Dinner!");
				else
					println("Screw this, I'm out!");
			}
		}
		
		//print
		private void println(String msg) {
			System.out.println("<ReferencePlayer" + id + ">: " + msg);
		}
		
		private void println() {
			System.out.println();
		}
	}
	
	@FunctionalInterface
	public interface ImportanceFunction {
		
		int importance(int shipLengthId, int shipPlacementPossibilities, int shipCount);
		
	}
	
	public static ImportanceFunction createImportanceFunctionFromArray(int[] importanceArray) {
		return (shipLengthId, pos, shipCount) -> shipCount == 0 ? 0 : importanceArray[shipLengthId] * shipCount * pos;
	}
	
	public static ImportanceFunction createImportanceFunctionFromArray(float[] importanceArray) {
		return (shipLengthId, pos, shipCount) -> shipCount == 0 ? 0 : (int) (importanceArray[shipLengthId] * shipCount * pos);
	}
	
}
