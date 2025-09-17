package it.polito.extgol;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

/**
 * Facade coordinating the core operations of the Extended Game of Life
 * simulation.
 *
 * This class provides high-level methods to: - Evolve a single generation or
 * advance multiple steps. - Visualize the board state and retrieve alive cells
 * by coordinate. - Persist and reload entire game instances.
 */
public class ExtendedGameOfLife {

    private Map<Integer, EventType> eventsMap = new HashMap<>();

    public Boolean areInteractable(Cell c, Cell n) {
        return (n.getY() > c.getY()) || (n.getY() == c.getY() && n.getX() > c.getX());
    }

    /**
     * Computes and returns the next generation based on the current one.
     *
     * The method follows these steps: 1. Validates that the current generation
     * has an associated Board and Game. 2. Computes the next alive/dead state
     * for each cell based solely on the current state. 3. Creates a new
     * Generation object representing the next simulation step. 4. Applies all
     * calculated state changes simultaneously, ensuring consistency. 5.
     * Captures a snapshot of all cells' states into the persistent map for
     * future retrieval.
     *
     * @param current The current generation snapshot used for evolving to the
     *                next state.
     * @return A new Generation object reflecting the evolved board state.
     * @throws IllegalStateException If Generation is not properly initialized.
     */
    public Generation evolve(Generation current) {
        Objects.requireNonNull(current, "Current generation cannot be null");
        Board board = current.getBoard();
        Game game = current.getGame();

        if (board == null || game == null) {
            throw new IllegalStateException(
                    "Generation must have associated Board and Game!");
        }

        List<Cell> orderedCells = new ArrayList<>(board.getCellSet());
        orderedCells.sort(Comparator.comparing((Cell c) -> c.getY())
                .thenComparing((Cell c) -> c.getX()));
        for (int i = 0; i < orderedCells.size() - 1; i++) {
            Cell c = orderedCells.get(i);
            for (Tile neighborTile : c.getNeighbors()) {
                Cell neighbor = neighborTile.getCell();
                if (neighbor != null && areInteractable(c, neighbor)) {
                    c.interact(neighbor);
                }
            }
        }

        for (Tile tile : board.getTiles()) {
            Cell c = tile.getCell();
            if (c != null && c.getIsInfected() && c.getMood() == CellMood.NAIVE) {
                c.setMood(CellMood.VAMPIRE);
                c.setIsInfected(false);
            }
        }

        Map<Cell, Boolean> nextStates = new HashMap<>();
        Map<Cell, Integer> newLPs = new HashMap<>();
        EventType currentEvent = eventsMap.get(current.getStep());
        for (Tile tile : board.getTiles()) {
            Cell c = tile.getCell();
            if (c == null) {
                throw new IllegalStateException("Missing cell on tile " + tile);
            }

            int prevLP = current.getEnergyStates().get(c);

            int base;
            if (currentEvent == EventType.BLOOM
                    || currentEvent == EventType.FAMINE
                    || currentEvent == EventType.CATACLYSM) {
                base = prevLP;
            } else {
                base = prevLP;
                if (c.isAlive() && currentEvent != EventType.SANCTUARY) {
                    base += tile.getLifePointModifier();
                }
            }

            int aliveNeighbors = c.countAliveNeighbors();
            boolean wasAlive = c.isAlive();
            boolean nextState = c.evolve(aliveNeighbors);

            int newLP = 0;

            if (currentEvent == EventType.CATACLYSM && c.isAlive()) {
                prevLP = 0;
                base = 0;
            }

            if (currentEvent == EventType.SANCTUARY && c.getMood() == CellMood.VAMPIRE) {
                newLP = prevLP;
                c.setMood(CellMood.NAIVE);
            } else if (c.getMood() == CellMood.VAMPIRE) {
                int absorbedLP = 0;
                for (Tile neighborTile : c.getNeighbors()) {
                    Cell neighbor = neighborTile.getCell();
                    if (neighbor != null && neighbor.getMood() == CellMood.NAIVE && neighbor.isAlive()) {
                        absorbedLP += neighbor.getLifePoints();
                        if (currentEvent == EventType.BLOOD_MOON) {
                            neighbor.setIsInfected(false);
                        } else {
                            neighbor.setIsInfected(true);
                        }
                        neighbor.setLifePoints(0);
                    }
                }
                newLP = c.getLifePoints() + absorbedLP;
            } else if (currentEvent == EventType.BLOOM) {
                if (wasAlive) {
                    newLP = nextState ? base + 3 : base - 1;
                } else {
                    newLP = Math.min(0, base + 2);
                }
            } else if (currentEvent == EventType.FAMINE) {
                int bonus = 0;
                if (wasAlive) {
                    bonus = nextState ? 1 : -1;
                }
                newLP = base + bonus;
            } else if (!wasAlive && nextState) {
                newLP = 0;
            } else {
                int bonus = 0;
                if (wasAlive) {
                    bonus = nextState ? 1 : -1;
                } else {
                    bonus = nextState ? 1 : 0;
                }
                newLP = base + bonus;
            }

            if (newLP < 0 && nextState) {
                nextState = false;
            }

            nextStates.put(c, nextState);
            newLPs.put(c, newLP);
        }

        Generation nextGen = Generation.createNextGeneration(current);

        for (Map.Entry<Cell, Boolean> e : nextStates.entrySet()) {
            Cell c = e.getKey();
            c.setAlive(e.getValue());
            c.setLifePoints(newLPs.get(c));
            c.addGeneration(nextGen);
            nextGen.setEnergyState(c, newLPs.get(c));
        }
        nextGen.snapCells();

        return nextGen;
    }

    /**
     * Advances the simulation by evolving the game state through a given number
     * of steps.
     *
     * Starting from the game's initial generation, this method repeatedly
     * computes the next generation and appends it to the game's history.
     *
     * @param game  The Game instance whose generations will be advanced.
     * @param steps The number of evolution steps (generations) to perform.
     * @return The same Game instance, updated with the new generation.
     */
    public Game run(Game game, int steps) {
        Generation current = game.getStart();
        for (int i = 0; i < steps; i++) {
            Generation next = evolve(current);
            current = next;
        }
        return game;
    }

    /**
     * Advances the simulation by evolving the game state through a given number
     * of steps.
     *
     * Starting from the game's initial generation, this method repeatedly
     * computes the next generation and appends it to the game's history.
     *
     * It applies any events at their scheduled generations.
     *
     * At each step: 1. If an event is scheduled for the current step (according
     * to eventMap), the corresponding event is applied to all tiles before
     * evolution. 2. The board then evolves to the next generation, which is
     * added to the game.
     *
     * @param game     The Game instance to run and update.
     * @param steps    The total number of generations to simulate.
     * @param eventMap A map from generation index (0-based) to the EventType to
     *                 trigger; if a step is not present in the map, no event is
     *                 applied that
     *                 step.
     * @return The same Game instance, now containing the extended generation
     *         history.
     */
    public Game run(Game game, int steps, Map<Integer, EventType> eventMap) {
        Generation current = game.getStart();

        this.eventsMap.clear();
        this.eventsMap.putAll(eventMap);

        for (int gen = 0; gen < steps; gen++) {

            EventType event = eventMap.get(gen);
            if (event != null && event != EventType.CATACLYSM) {
                processEvent(game.getBoard(), event);
                current.snapCells();
            }
            Generation next = this.evolve(current);
            game.addGeneration(next);
            current = next;
        }
        return game;
    }

    private void processEvent(Board board, EventType event) {
        switch (event) {
            case BLOOM:
                board.getTiles().forEach(t -> {
                    Cell c = t.getCell();
                    if (c != null) {
                        c.setLifePoints(c.getLifePoints());
                    }
                });
                break;
            case CATACLYSM:
                board.getTiles().forEach(t -> {
                    Cell c = t.getCell();
                    if (c != null) {
                        c.setLifePoints(0);
                        c.setAlive(false);
                    }
                });
                break;
            case FAMINE:
                board.getTiles().forEach(t -> {
                    Cell c = t.getCell();
                    int newLifePoints = c.getLifePoints() - 1;
                    c.setLifePoints(newLifePoints);
                    if (newLifePoints < 0) {
                        c.setAlive(false);
                    }
                });
                break;
            case BLOOD_MOON:
                // R1/R2: To be implemented
                break;

            case SANCTUARY:
                board.getTiles().forEach(t -> {
                    Cell c = t.getCell();
                    if (c != null && c.isAlive()) {
                        if (c.getMood() == CellMood.VAMPIRE) {
                            c.setLifePoints(c.getLifePoints() + 3);
                        } else {
                            c.setLifePoints(c.getLifePoints() + 1);
                        }
                    }
                });
                break;
            default:
                break;

        }
    }

    /**
     * Builds and returns a map associating each coordinate with its alive Cell
     * instance for the specified generation.
     *
     * Iterates over all alive cells present in the given generation and
     * constructs a coordinate-based map, facilitating cell access.
     *
     * @param generation The generation whose alive cells are mapped.
     * @return A Map from Coord (coordinates) to Cell instances representing all
     *         alive cells.
     */
    public Map<Coord, Cell> getAliveCells(Generation generation) {
        Map<Coord, Cell> alive = new HashMap<>();
        for (Cell c : generation.getAliveCells()) {
            alive.put(c.getCoordinates(), c);
        }
        return alive;
    }

    /**
     * Generates a visual string representation of the specified generation's
     * board state.
     *
     * It produces a multi-line textual snapshot showing cells and their status.
     * "C" -> alive cell "0" -> dead cell
     *
     * @param generation The Generation instance to visualize.
     * @return A multi-line String-based representiion of the board's current
     *         state.
     */
    public String visualize(Generation generation) {
        return generation.getBoard().visualize(generation);
    }

    /**
     * Persists the complete state of the provided Game instance, including its
     * Board, Tiles, Cells, and all associated Generations.
     *
     * If the Game is new, it will be created and persisted. Otherwise, its
     * state will be updated (merged) in the database. Ensures transactional
     * safety and consistency through commit and rollback handling.
     *
     * @param game The Game instance to persist or update.
     */
    public void saveGame(Game game) {
        EntityManager em = JPAUtil.getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            if (game.getId() == null) {
                em.persist(game);
            } else {
                em.merge(game);
            }
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    /**
     * Loads and returns a persisted map of game events keyed by generation
     * step.
     *
     * Delegates retrieval to the corresponding repository class, which in turn
     * implements the provided generic repository class for persistence. This
     * method reconstructs the event timeline for inspection or replay.
     *
     * @return A Map<Integer, EventType> mapping generation steps to associated
     *         events.
     */
    public Map<Integer, EventType> loadEvents() {
        return new HashMap<>(this.eventsMap);
    }

}
