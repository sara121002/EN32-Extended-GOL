package it.polito.extgol;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
public class GameRepository extends GenericExtGOLRepository<Game, Long> {

    private EntityManager em;

    public GameRepository() {
        super(Game.class);
    }

    public Game load(Long id) {
        TypedQuery<Game> q = em.createQuery(
                "SELECT DISTINCT g FROM Game g "
                + " LEFT JOIN FETCH g.board b"
                + " LEFT JOIN FETCH b.tiles t"
                + " LEFT JOIN FETCH g.generations gen"
                + " LEFT JOIN FETCH gen.cellAlivenessStates"
                + " LEFT JOIN FETCH gen.energyStates"
                + " LEFT JOIN FETCH gen.events"
                + " WHERE g.id = :id", Game.class);
        q.setParameter("id", id);
        return q.getResultStream().findFirst().orElse(null);
    }
}
