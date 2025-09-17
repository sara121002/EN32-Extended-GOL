package it.polito.extgol;

import jakarta.persistence.Entity;

@Entity
public class Highlander extends Cell {

    private int countGenerations= 0;
    public Highlander(){
        
    }
    public Highlander(Coord tileCoord, Tile t, Board b, Game g) {
        super(tileCoord, t, b, g);
    }

    @Override
    public Boolean evolve(int aliveNeighbors) {
        
        boolean hasTolive = super.evolve(aliveNeighbors);

        if (!hasTolive){
            if (countGenerations <3){
                countGenerations++;
                return true;
            }
            else{
                return false;
            }
        }
        else{
            countGenerations = 0;
            return true;
        }

    }
}
