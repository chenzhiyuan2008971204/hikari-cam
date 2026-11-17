// Draws a tile of the image. aPos is the unit quad, (0,0) = tile top-left.
in vec2 aPos;
uniform vec4 uTile;   // tile origin and size in image coordinates (origin top-left)
uniform float uFlipY; // 1: on screen (NDC up = image top). 0: read back (row 0 = image top)
out vec2 vUv;
void main() {
    vUv = uTile.xy + aPos * uTile.zw;
    float y = uFlipY > 0.5 ? 1.0 - aPos.y * 2.0 : aPos.y * 2.0 - 1.0;
    gl_Position = vec4(aPos.x * 2.0 - 1.0, y, 0.0, 1.0);
}
