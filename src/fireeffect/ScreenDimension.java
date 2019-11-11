package fireeffect;

import java.util.Objects;

public class ScreenDimension {
    int w;
    int h;
    public ScreenDimension(int w, int h) {
        this.w = w;
        this.h = h;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScreenDimension)) return false;
        ScreenDimension that = (ScreenDimension) o;
        return w == that.w &&
                h == that.h;
    }

    public boolean equals(int w, int h) {
        return this.w == w &&
                this.h == h;
    }

    @Override
    public int hashCode() {
        return Objects.hash(w, h);
    }
}
