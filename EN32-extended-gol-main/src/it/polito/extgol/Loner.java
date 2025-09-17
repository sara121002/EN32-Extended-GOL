package it.polito.extgol;

import jakarta.persistence.Entity;

@Entity
public class Loner extends Cell {

    public Loner(){

    }

    public Loner(Coord tileCoord, Tile t, Board b, Game g){
        super(tileCoord, t, b, g);
        this.setAlive(false);
    }


    @Override
    public Boolean evolve(int aliveNeighbors) {
        // Start by assuming the cell retains its current state
        Boolean willLive = this.isAlive;
        
        // Overpopulation: more than 3 neighbors kills a live cell
        if (aliveNeighbors > 3) {
            willLive = false;
        }
        // Underpopulation: fewer than 1 neighbors kills a live cell
        else if (aliveNeighbors < 1) {
            willLive = false;
        }
        // Respawn: exactly 3 neighbors brings a dead cell to life
        else if (!this.isAlive && aliveNeighbors == 3) {
            willLive = true;
            lifepoints = 0;
        }
        // Otherwise (2 or 3 neighbors on a live cell) nothing changes and willLive
        // remains true

        if (this.isAlive() && !willLive){
            lifepoints--;
        }
        else if (this.isAlive() && willLive){
            lifepoints++;
        }

        return willLive;
    }

    
}
