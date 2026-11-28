<#
.SYNOPSIS
    Gera os ícones do app a partir da arte-mestre (icone-app.png, ao lado deste script).

.DESCRIPTION
    A arte-mestre é o quadrado amarelo com o símbolo (pin, estrada e check), com
    gradiente de amarelo e cantos pretos, sem transparência. Este script:
      1. mede o contorno do quadrado e o gradiente do fundo;
      2. separa o símbolo do fundo (para o ícone adaptativo);
      3. grava em app/src/main/res:
         - mipmap-*/ic_launcher.png e ic_launcher_round.png    (Android 7)
         - mipmap-*/ic_launcher_foreground.png                 (primeiro plano do ícone adaptativo)
         - drawable-xxxhdpi/ic_launcher_monochrome.png         (ícones temáticos do Android 13)
         - mipmap-xxxhdpi/ic_launcher-playstore.png            (loja, 512x512)
         - drawable-nodpi/app_logo.png                         (splash)
         - drawable/ic_launcher_gradient.xml e values/ic_launcher_background.xml
      4. escreve art/conferencia.png, uma folha para conferir o resultado.

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File art\gerar-icone.ps1
#>
param(
    [string]$Origem = (Join-Path $PSScriptRoot 'icone-app.png'),
    [string]$Res = (Join-Path $PSScriptRoot '..\app\src\main\res'),
    [string]$Folha = (Join-Path $PSScriptRoot 'conferencia.png')
)

Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @"
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
using System.Text;

public static class GeradorIcone
{
    // ===== arte-mestre =====
    static byte[] src; static int W, H;        // BGRA
    static int[] rowA, rowB;                   // contorno: primeiro e último amarelo puro de cada linha
    static int[] colTop, colBottom;            // contorno por coluna (cantos arredondados)
    static int top, bottom, left, right;       // limites do quadrado
    static double[] bR, bG, bB;                // fundo amarelo ajustado como plano (1, u, v) por canal

    // ===== camadas na resolução da fonte (RGBA pré-multiplicado) =====
    static byte[] simbolo;                     // só o desenho (pin, estrada e check), sem o fundo
    static byte[] quadrado;                    // o quadrado inteiro, com cantos transparentes
    static double cx0, cy0, cw, ch;            // caixa do desenho (cobertura >= 50%)

    // Cores de tinta do desenho: preenchimento escuro, contorno quase preto e branco
    static readonly int[][] Tinta = {
        new int[] { 20, 24, 34 }, new int[] { 4, 5, 9 }, new int[] { 255, 255, 255 }
    };

    // ================= leitura e medição =================

    static int Idx(int x, int y) { return (y * W + x) * 4; }
    static int R(int x, int y) { return src[Idx(x, y) + 2]; }
    static int G(int x, int y) { return src[Idx(x, y) + 1]; }
    static int B(int x, int y) { return src[Idx(x, y)]; }

    // Amarelo puro de fundo (não conta mistura com escuro nem com branco)
    static bool Fundo(int x, int y)
    {
        int r = R(x, y), g = G(x, y), b = B(x, y);
        return r >= 215 && g >= 170 && b <= 60;
    }

    // Lê a arte-mestre e deixa tudo medido e separado
    public static void Preparar(string caminho)
    {
        using (var imagem = new Bitmap(caminho))
        using (var b = new Bitmap(imagem.Width, imagem.Height, PixelFormat.Format32bppArgb))
        {
            using (var g = Graphics.FromImage(b)) { g.DrawImageUnscaled(imagem, 0, 0); }
            W = b.Width; H = b.Height;
            src = new byte[W * H * 4];
            var dados = b.LockBits(new Rectangle(0, 0, W, H), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
            Marshal.Copy(dados.Scan0, src, 0, src.Length);
            b.UnlockBits(dados);
        }
        MedirContorno();
        AjustarFundo();
        SepararCamadas();
    }

    static void MedirContorno()
    {
        rowA = new int[H]; rowB = new int[H];
        top = -1; bottom = -1; left = int.MaxValue; right = -1;
        for (int y = 0; y < H; y++)
        {
            rowA[y] = -1; rowB[y] = -1;
            for (int x = 0; x < W; x++) if (Fundo(x, y)) { rowA[y] = x; break; }
            if (rowA[y] < 0) continue;
            for (int x = W - 1; x >= 0; x--) if (Fundo(x, y)) { rowB[y] = x; break; }
            if (top < 0) top = y;
            bottom = y;
            if (rowA[y] < left) left = rowA[y];
            if (rowB[y] > right) right = rowB[y];
        }
        if (top < 0) throw new Exception("Não achei o quadrado amarelo na arte-mestre.");

        colTop = new int[W]; colBottom = new int[W];
        for (int x = 0; x < W; x++)
        {
            colTop[x] = -1; colBottom[x] = -1;
            for (int y = 0; y < H; y++) if (Fundo(x, y)) { colTop[x] = y; break; }
            if (colTop[x] < 0) continue;
            for (int y = H - 1; y >= 0; y--) if (Fundo(x, y)) { colBottom[x] = y; break; }
        }
    }

    // Fundo amarelo como plano por canal, ajustado só em pontos com vizinhança toda amarela
    static void AjustarFundo()
    {
        var X = new List<double[]>(); var Y = new List<double[]>();
        for (int y = top + 8; y <= bottom - 8; y += 3) for (int x = rowA[y] + 8; x <= rowB[y] - 8; x += 3)
        {
            bool limpo = true;
            for (int dy = -7; dy <= 7 && limpo; dy += 2) for (int dx = -7; dx <= 7; dx += 2)
                if (rowA[y + dy] < 0 || x + dx <= rowA[y + dy] + 3 || x + dx >= rowB[y + dy] - 3 || !Fundo(x + dx, y + dy)) { limpo = false; break; }
            if (!limpo) continue;
            X.Add(new double[] { 1, (double)x / W, (double)y / H });
            Y.Add(new double[] { R(x, y), G(x, y), B(x, y) });
        }
        bR = Ajustar(X, Y, 0); bG = Ajustar(X, Y, 1); bB = Ajustar(X, Y, 2);
    }

    static double[] Ajustar(List<double[]> X, List<double[]> Y, int canal)
    {
        var A = new double[3, 3]; var b = new double[3];
        for (int i = 0; i < X.Count; i++)
            for (int a = 0; a < 3; a++)
            {
                b[a] += X[i][a] * Y[i][canal];
                for (int c = 0; c < 3; c++) A[a, c] += X[i][a] * X[i][c];
            }
        for (int i = 0; i < 3; i++)
        {
            int p = i;
            for (int r = i + 1; r < 3; r++) if (Math.Abs(A[r, i]) > Math.Abs(A[p, i])) p = r;
            for (int c = 0; c < 3; c++) { double t = A[i, c]; A[i, c] = A[p, c]; A[p, c] = t; }
            double tb = b[i]; b[i] = b[p]; b[p] = tb;
            for (int r = 0; r < 3; r++)
            {
                if (r == i) continue;
                double f = A[r, i] / A[i, i];
                for (int c = i; c < 3; c++) A[r, c] -= f * A[i, c];
                b[r] -= f * b[i];
            }
        }
        return new double[] { b[0] / A[0, 0], b[1] / A[1, 1], b[2] / A[2, 2] };
    }

    // Cor do fundo amarelo em um ponto da arte-mestre (em px)
    static void Amarelo(double x, double y, out double r, out double g, out double b)
    {
        double u = x / W, v = y / H;
        r = bR[0] + bR[1] * u + bR[2] * v;
        g = bG[0] + bG[1] * u + bG[2] * v;
        b = bB[0] + bB[1] * u + bB[2] * v;
    }

    static void Pintar(byte[] p, int i, double r, double g, double b, double a)
    {
        p[i] = (byte)Math.Round(b * a); p[i + 1] = (byte)Math.Round(g * a);
        p[i + 2] = (byte)Math.Round(r * a); p[i + 3] = (byte)Math.Round(255 * a);
    }

    // ================= separação do desenho =================

    static void SepararCamadas()
    {
        int n = W * H * 4;
        simbolo = new byte[n]; quadrado = new byte[n];

        for (int y = top - 1; y <= bottom + 1; y++)
        {
            int ye = y < top ? top : (y > bottom ? bottom : y);
            // Linha sem nenhum amarelo: no rodapé do quadrado a sombra cobre tudo.
            // Entra no ícone quadrado com a cor original, mas não é desenho.
            bool semAmarelo = rowA[ye] < 0;
            int xa = semAmarelo ? left : rowA[ye], xb = semAmarelo ? right : rowB[ye];
            for (int x = xa - 1; x <= xb + 1; x++)
            {
                bool puro = y >= top && y <= bottom && x >= xa && x <= xb;
                bool beirada = !puro || x == xa || x == xb || y == top || y == bottom;
                double bgR, bgG, bgB; Amarelo(x + 0.5, y + 0.5, out bgR, out bgG, out bgB);
                int i = Idx(x, y);

                if (semAmarelo)
                {
                    // Linha de sombra pura no rodapé: o recorte aqui é só pelas
                    // colunas (não há contorno por linha). Pinta o gradiente com a
                    // cobertura — a cor original aqui é a sombra, e misturar com ela
                    // deixava pontos escuros na borda de baixo do ícone.
                    double covL = Math.Max(0, Math.Min(1, x - left + 0.5));
                    double covR = Math.Max(0, Math.Min(1, right - x + 0.5));
                    double covT = colTop[x] < 0 ? 0 : Math.Max(0, Math.Min(1, y - colTop[x] + 0.5));
                    double covB = colBottom[x] < 0 ? 0 : Math.Max(0, Math.Min(1, colBottom[x] - y + 0.5));
                    double cov = Math.Min(Math.Min(covL, covR), Math.Min(covT, covB));
                    if (cov <= 0) continue;
                    Pintar(quadrado, i, bgR, bgG, bgB, cov);
                    continue;
                }

                if (beirada)
                {
                    // Beirada do quadrado: recorte pelo contorno medido (linhas E colunas),
                    // com ~1 px de transição. A cor é o amarelo do gradiente — misturar a
                    // cor observada aqui deixava uma franja cinza nos cantos arredondados.
                    double covL = Math.Max(0, Math.Min(1, x - xa + 0.5));
                    double covR = Math.Max(0, Math.Min(1, xb - x + 0.5));
                    double covT = colTop[x] < 0 ? 0 : Math.Max(0, Math.Min(1, y - colTop[x] + 0.5));
                    double covB = colBottom[x] < 0 ? 0 : Math.Max(0, Math.Min(1, colBottom[x] - y + 0.5));
                    double cov = Math.Min(Math.Min(covL, covR), Math.Min(covT, covB));
                    Pintar(quadrado, i, bgR, bgG, bgB, cov);
                    continue;
                }

                // Dentro do quadrado: o ícone antigo e o logo ficam com a cor original
                Pintar(quadrado, i, R(x, y), G(x, y), B(x, y), 1);

                // Desenho: acha a tinta e a cobertura que, sobre o fundo amarelo, dão este pixel
                double vx = R(x, y) - bgR, vy = G(x, y) - bgG, vz = B(x, y) - bgB;
                double melhor = double.MaxValue, ms = 0; int[] mk = Tinta[0];
                for (int t = 0; t < Tinta.Length; t++)
                {
                    int[] k = Tinta[t];
                    double dx = k[0] - bgR, dy = k[1] - bgG, dz = k[2] - bgB;
                    double s = (vx * dx + vy * dy + vz * dz) / (dx * dx + dy * dy + dz * dz);
                    double ex = vx - s * dx, ey = vy - s * dy, ez = vz - s * dz;
                    double afastamento = ex * ex + ey * ey + ez * ez;
                    if (afastamento < melhor) { melhor = afastamento; ms = s; mk = k; }
                }
                double a = ms < 0.02 ? 0 : Math.Min(ms, 1);
                if (a <= 0) continue;

                double cr, cg, cb;
                if (a >= 0.6)
                {
                    // Cobertura alta: recupera a cor real (mantém o sombreado interno)
                    cr = (R(x, y) - (1 - a) * bgR) / a;
                    cg = (G(x, y) - (1 - a) * bgG) / a;
                    cb = (B(x, y) - (1 - a) * bgB) / a;
                }
                else { cr = mk[0]; cg = mk[1]; cb = mk[2]; }
                Pintar(simbolo, i, cr, cg, cb, a);
            }
        }

        // O desenho só existe perto de si mesmo. Longe dele, o que a separação
        // achou é variação do fundo (o amarelo escurece nas laterais do quadrado,
        // e as bordas de baixo viram sombra pura), e isso não pode virar uma
        // sombra quadrada no ícone adaptativo.
        var forte = new bool[W * H];
        for (int i = 0; i < simbolo.Length; i += 4) if (simbolo[i + 3] >= 90) forte[i / 4] = true;
        bool[] perto = Perto(forte, 70);
        for (int i = 0; i < simbolo.Length; i += 4)
            if (!perto[i / 4]) { simbolo[i] = simbolo[i + 1] = simbolo[i + 2] = simbolo[i + 3] = 0; }

        // Caixa do desenho: só DEPOIS da poda, com pixels de cobertura alta —
        // assim a sombra difusa não estica a caixa e o símbolo sai no tamanho certo
        double cx1 = -1, cy1 = -1; cx0 = double.MaxValue; cy0 = double.MaxValue;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
        {
            int i = Idx(x, y) + 3;
            if (simbolo[i] >= 128)
            {
                if (x < cx0) cx0 = x; if (x > cx1) cx1 = x;
                if (y < cy0) cy0 = y; if (y > cy1) cy1 = y;
            }
        }
        cw = cx1 - cx0 + 1; ch = cy1 - cy0 + 1;
    }

    // Marca os pixels a até "raio" px de algum pixel marcado (varredura na horizontal e na vertical)
    static bool[] Perto(bool[] marcado, int raio)
    {
        const int longe = 1 << 20;
        int[] d = new int[W * H];
        for (int y = 0; y < H; y++)
        {
            int v = longe;
            for (int x = 0; x < W; x++) { int i = y * W + x; v = marcado[i] ? 0 : Math.Min(v + 1, longe); d[i] = v; }
            v = longe;
            for (int x = W - 1; x >= 0; x--) { int i = y * W + x; v = marcado[i] ? 0 : Math.Min(v + 1, longe); if (v < d[i]) d[i] = v; }
        }
        for (int x = 0; x < W; x++)
        {
            int v = longe;
            for (int y = 0; y < H; y++) { int i = y * W + x; v = Math.Min(v + 1, d[i]); d[i] = v; }
            v = longe;
            for (int y = H - 1; y >= 0; y--) { int i = y * W + x; v = Math.Min(v + 1, d[i]); d[i] = v; }
        }
        var saida = new bool[W * H];
        for (int i = 0; i < d.Length; i++) saida[i] = d[i] <= raio;
        return saida;
    }

    // ================= montagem =================

    static byte[] Novo(int D) { return new byte[D * D * 4]; }

    // Cola o retângulo da fonte [sx,sy,sw,sh] no retângulo do destino [du,dv,dw,dh] (em px),
    // por cima do que já existe, com média por área.
    static void Colar(byte[] dest, int DW, int DH, byte[] s, int SW, int SH,
                      double sx, double sy, double sw, double sh, double du, double dv, double dw, double dh)
    {
        double fx = sw / dw, fy = sh / dh;
        for (int y = 0; y < DH; y++)
        {
            double sy0 = (y - dv) * fy + sy, sy1 = (y + 1 - dv) * fy + sy;
            int iy0 = (int)Math.Floor(sy0), iy1 = (int)Math.Ceiling(sy1);
            for (int x = 0; x < DW; x++)
            {
                double sx0 = (x - du) * fx + sx, sx1 = (x + 1 - du) * fx + sx;
                int ix0 = (int)Math.Floor(sx0), ix1 = (int)Math.Ceiling(sx1);
                double r = 0, g = 0, b = 0, a = 0;
                for (int iy = iy0; iy < iy1; iy++)
                {
                    if (iy < 0 || iy >= SH) continue;
                    double oy = Math.Min(iy + 1, sy1) - Math.Max(iy, sy0);
                    if (oy <= 0) continue;
                    for (int ix = ix0; ix < ix1; ix++)
                    {
                        if (ix < 0 || ix >= SW) continue;
                        double ox = Math.Min(ix + 1, sx1) - Math.Max(ix, sx0);
                        if (ox <= 0) continue;
                        double peso = ox * oy; int i = (iy * SW + ix) * 4;
                        b += s[i] * peso; g += s[i + 1] * peso; r += s[i + 2] * peso; a += s[i + 3] * peso;
                    }
                }
                double area = fx * fy;
                if (area <= 0) continue;
                int o = (y * DW + x) * 4;
                // "Por cima": a fonte cobre esta fração do pixel e o resto fica com o destino
                double cobre = a / area / 255.0, sobra = 1 - cobre;
                dest[o] = (byte)Math.Min(255, b / area + dest[o] * sobra);
                dest[o + 1] = (byte)Math.Min(255, g / area + dest[o + 1] * sobra);
                dest[o + 2] = (byte)Math.Min(255, r / area + dest[o + 2] * sobra);
                dest[o + 3] = (byte)Math.Min(255, a / area + dest[o + 3] * sobra);
            }
        }
    }

    // Fundo com o gradiente de amarelo da arte-mestre
    static void PreencherFundo(byte[] c, int D)
    {
        double tw = right - left + 1, th = bottom - top + 1;
        for (int y = 0; y < D; y++) for (int x = 0; x < D; x++)
        {
            double r, g, b; Amarelo(left + (x + 0.5) * tw / D, top + (y + 0.5) * th / D, out r, out g, out b);
            Pintar(c, (y * D + x) * 4, r, g, b, 1);
        }
    }

    // Desenho no centro do quadrado D, com a diagonal da sua caixa medindo "diagonal" px
    static void Desenho(byte[] c, int D, double diagonal)
    {
        double escala = diagonal / Math.Sqrt(cw * cw + ch * ch);
        double du = D / 2.0 - (cx0 + cw / 2) * escala, dv = D / 2.0 - (cy0 + ch / 2) * escala;
        Colar(c, D, D, simbolo, W, H, 0, 0, W, H, du, dv, W * escala, H * escala);
    }

    static byte[] Recortar(byte[] p, int D, int x0, int y0, int C)
    {
        var r = new byte[C * C * 4];
        for (int y = 0; y < C; y++) Buffer.BlockCopy(p, ((y0 + y) * D + x0) * 4, r, y * C * 4, C * 4);
        return r;
    }

    // Multiplica a cobertura pela forma da máscara (com anti-alias por subamostragem 8x8)
    static void Mascara(byte[] p, int D, Func<double, double, bool> dentro)
    {
        const int N = 8;
        for (int y = 0; y < D; y++) for (int x = 0; x < D; x++)
        {
            int conta = 0;
            for (int j = 0; j < N; j++) for (int i = 0; i < N; i++)
                if (dentro((x + (i + 0.5) / N) / D, (y + (j + 0.5) / N) / D)) conta++;
            double c = (double)conta / (N * N);
            int o = (y * D + x) * 4;
            p[o] = (byte)(p[o] * c); p[o + 1] = (byte)(p[o + 1] * c);
            p[o + 2] = (byte)(p[o + 2] * c); p[o + 3] = (byte)(p[o + 3] * c);
        }
    }

    static readonly Func<double, double, bool> Circulo = delegate(double u, double v)
    {
        double dx = u - 0.5, dy = v - 0.5; return dx * dx + dy * dy <= 0.25;
    };
    static readonly Func<double, double, bool> Arredondado = delegate(double u, double v)
    {
        const double r = 0.24;
        double dx = Math.Max(Math.Abs(u - 0.5) - (0.5 - r), 0), dy = Math.Max(Math.Abs(v - 0.5) - (0.5 - r), 0);
        return dx * dx + dy * dy <= r * r;
    };
    static readonly Func<double, double, bool> Superelipse = delegate(double u, double v)
    {
        return Math.Pow(Math.Abs(2 * u - 1), 5) + Math.Pow(Math.Abs(2 * v - 1), 5) <= 1;
    };

    // Codifica em PNG, desfazendo a pré-multiplicação em uma cópia
    static byte[] Png(byte[] premul, int D)
    {
        byte[] reto = (byte[])premul.Clone();
        for (int i = 0; i < reto.Length; i += 4)
        {
            int a = reto[i + 3];
            if (a == 0 || a == 255) continue;
            for (int c = 0; c < 3; c++) reto[i + c] = (byte)Math.Min(255, Math.Round(reto[i + c] * 255.0 / a));
        }
        using (var b = new Bitmap(D, D, PixelFormat.Format32bppArgb))
        {
            var dados = b.LockBits(new Rectangle(0, 0, D, D), ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
            Marshal.Copy(reto, 0, dados.Scan0, reto.Length);
            b.UnlockBits(dados);
            using (var ms = new System.IO.MemoryStream()) { b.Save(ms, ImageFormat.Png); return ms.ToArray(); }
        }
    }

    // ================= saídas =================
    // Montar* devolve RGBA pré-multiplicado; os métodos públicos devolvem o PNG pronto.

    static byte[] MontarQuadrado(int D)
    {
        byte[] c = Novo(D);
        Colar(c, D, D, quadrado, W, H, left, top, right - left + 1, bottom - top + 1, 0, 0, D, D);
        return c;
    }

    static byte[] MontarRedondo(int D)
    {
        byte[] c = Novo(D);
        PreencherFundo(c, D);
        Desenho(c, D, 0.86 * D);
        Mascara(c, D, Circulo);
        return c;
    }

    // Primeiro plano do ícone adaptativo: o desenho cabe na zona segura (66 de 108dp)
    static byte[] MontarPrimeiroPlano(int D)
    {
        byte[] c = Novo(D);
        Desenho(c, D, 62.0 * D / 108);
        return c;
    }

    static byte[] MontarMonocromatico(int D)
    {
        byte[] c = MontarPrimeiroPlano(D);
        for (int i = 0; i < c.Length; i += 4) { c[i] = c[i + 1] = c[i + 2] = c[i + 3]; }
        return c;
    }

    // Ícone da loja: quadrado cheio, sem cantos (a loja aplica o próprio recorte).
    // Fundo liso do gradiente com o desenho no lugar original.
    static byte[] MontarLoja(int D)
    {
        byte[] c = Novo(D);
        PreencherFundo(c, D);
        Colar(c, D, D, simbolo, W, H, left, top, right - left + 1, bottom - top + 1, 0, 0, D, D);
        return c;
    }

    // Ícone do Android 7: o quadrado da arte-mestre com cantos transparentes (também é o logo do splash)
    public static byte[] Quadrado(int D) { return Png(MontarQuadrado(D), D); }
    public static byte[] Redondo(int D) { return Png(MontarRedondo(D), D); }
    public static byte[] PrimeiroPlano(int D) { return Png(MontarPrimeiroPlano(D), D); }
    public static byte[] Monocromatico(int D) { return Png(MontarMonocromatico(D), D); }
    public static byte[] Loja(int D) { return Png(MontarLoja(D), D); }

    public static string CorTopo() { double r, g, b; Amarelo((left + right) / 2.0, top + 0.5, out r, out g, out b); return Hex(r, g, b); }
    public static string CorMedia() { double r, g, b; Amarelo((left + right) / 2.0, (top + bottom) / 2.0, out r, out g, out b); return Hex(r, g, b); }
    public static string CorBase() { double r, g, b; Amarelo((left + right) / 2.0, bottom - 0.5, out r, out g, out b); return Hex(r, g, b); }

    static string Hex(double r, double g, double b)
    {
        return string.Format("#{0:X2}{1:X2}{2:X2}", (int)Math.Round(r), (int)Math.Round(g), (int)Math.Round(b));
    }

    public static string Relatorio()
    {
        var sb = new StringBuilder();
        sb.AppendLine(string.Format("quadrado: x {0}..{1} ({2} px), y {3}..{4} ({5} px), na imagem {6}x{7}",
            left, right, right - left + 1, top, bottom, bottom - top + 1, W, H));
        sb.AppendLine("fundo amarelo: topo " + CorTopo() + ", meio " + CorMedia() + ", base " + CorBase());
        sb.AppendLine(string.Format("desenho: caixa {0}x{1} px; margens ate a borda do quadrado: esq {2}, topo {3}, dir {4}, baixo {5}; diagonal/lado = {6:F3}",
            (int)cw, (int)ch, (int)(cx0 - left), (int)(cy0 - top), (int)(right - cx0 - cw), (int)(bottom - cy0 - ch),
            Math.Sqrt(cw * cw + ch * ch) / (right - left + 1)));
        return sb.ToString();
    }

    // ================= folha de conferência =================

    static Bitmap BitmapDe(byte[] premul, int D)
    {
        var b = new Bitmap(D, D, PixelFormat.Format32bppArgb);
        var dados = b.LockBits(new Rectangle(0, 0, D, D), ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
        var reto = (byte[])premul.Clone();
        for (int i = 0; i < reto.Length; i += 4)
        {
            int a = reto[i + 3];
            if (a == 0 || a == 255) continue;
            for (int c = 0; c < 3; c++) reto[i + c] = (byte)Math.Min(255, Math.Round(reto[i + c] * 255.0 / a));
        }
        Marshal.Copy(reto, 0, dados.Scan0, reto.Length);
        b.UnlockBits(dados);
        return b;
    }

    static void Desenhar(Graphics g, byte[] premul, int D, int x, int y, int lado)
    {
        using (var b = BitmapDe(premul, D))
        using (var at = new ImageAttributes())
        {
            at.SetWrapMode(WrapMode.TileFlipXY);
            g.DrawImage(b, new Rectangle(x, y, lado, lado), 0, 0, D, D, GraphicsUnit.Pixel, at);
        }
    }

    static void Rotulo(Graphics g, Font f, Brush cor, int x, int y, string texto) { g.DrawString(texto, f, cor, x, y); }

    // Ícone adaptativo como o launcher mostra: 72dp visíveis no meio dos 108dp, com a máscara
    static byte[] Adaptativo(Func<double, double, bool> mascara)
    {
        const int D = 432, C = 288;
        byte[] c = Novo(D);
        PreencherFundo(c, D);
        Desenho(c, D, 62.0 * D / 108);
        byte[] v = Recortar(c, D, (D - C) / 2, (D - C) / 2, C);
        Mascara(v, C, mascara);
        return v;
    }

    static void Pin(Graphics g, Rectangle area, Color tinta, Color fundo)
    {
        var escala = new Matrix();
        escala.Scale(area.Width / 24f, area.Height / 24f);
        escala.Translate(area.X, area.Y, MatrixOrder.Append);   // primeiro amplia, depois posiciona
        using (var p = new GraphicsPath())
        {
            p.AddEllipse(5f, 2f, 14f, 14f);
            p.AddPolygon(new PointF[] { new PointF(5.2f, 10.5f), new PointF(12f, 22f), new PointF(18.8f, 10.5f) });
            p.Transform(escala);
            using (var pincel = new SolidBrush(tinta)) { g.SmoothingMode = SmoothingMode.AntiAlias; g.FillPath(pincel, p); }
        }
        using (var furo = new GraphicsPath())
        {
            furo.AddEllipse(9.5f, 6.5f, 5f, 5f);
            furo.Transform(escala);
            using (var pincel = new SolidBrush(fundo)) { g.FillPath(pincel, furo); }
        }
    }

    public static void Folha(string caminho)
    {
        const int L = 1180, A = 780;
        using (var folha = new Bitmap(L, A, PixelFormat.Format24bppRgb))
        using (var g = Graphics.FromImage(folha))
        using (var titulo = new Font("Segoe UI", 11f, FontStyle.Bold))
        using (var texto = new Font("Segoe UI", 9.5f))
        using (var cinza = new SolidBrush(Color.FromArgb(0x49, 0x50, 0x57)))
        using (var claro = new SolidBrush(Color.FromArgb(0xE9, 0xE9, 0xEE)))
        using (var escuro = new SolidBrush(Color.FromArgb(0x1B, 0x1B, 0x22)))
        {
            g.Clear(Color.FromArgb(0xF2, 0xF2, 0xF7));
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;

            // --- linha 1: ícone do Android 7, redondo, loja e splash ---
            Rotulo(g, titulo, cinza, 24, 16, "Ícone do Android 7 (comum e redondo), ícone da loja e símbolo no splash");
            g.FillRectangle(claro, 24, 44, 1132, 224);
            Desenhar(g, MontarQuadrado(192), 192, 40, 60, 192);
            Desenhar(g, MontarQuadrado(96), 96, 256, 108, 96);
            Desenhar(g, MontarQuadrado(48), 48, 376, 132, 48);
            Desenhar(g, MontarRedondo(192), 192, 460, 60, 192);
            Desenhar(g, MontarRedondo(96), 96, 676, 108, 96);
            Desenhar(g, MontarRedondo(48), 48, 796, 132, 48);
            Desenhar(g, MontarLoja(512), 512, 880, 72, 128);
            g.FillRectangle(Brushes.White, 1004, 60, 136, 192);
            using (var faixa = new LinearGradientBrush(new Rectangle(1004, 60, 136, 70), Color.FromArgb(0xFF, 0xE6, 0x00), Color.White, 90f))
                g.FillRectangle(faixa, 1004, 60, 136, 70);
            Desenhar(g, MontarQuadrado(512), 512, 1008, 66, 128);
            Rotulo(g, texto, cinza, 1016, 214, "CONTROLE DE");
            Rotulo(g, texto, cinza, 1034, 230, "ESCALAS");

            // --- linha 2: adaptativo ---
            Rotulo(g, titulo, cinza, 24, 286, "Ícone adaptativo (Android 8 ou mais novo): círculo, arredondado e superelipse, em fundo claro e escuro");
            g.FillRectangle(claro, 24, 314, 552, 192);
            g.FillRectangle(escuro, 604, 314, 552, 192);
            var mascaras = new Func<double, double, bool>[] { Circulo, Arredondado, Superelipse };
            for (int i = 0; i < 3; i++)
            {
                byte[] v = Adaptativo(mascaras[i]);
                Desenhar(g, v, 288, 48 + i * 176, 330, 160);
                Desenhar(g, v, 288, 628 + i * 176, 330, 160);
            }

            // --- linha 3: silhueta temática e notificação ---
            Rotulo(g, titulo, cinza, 24, 532, "Silhueta dos ícones temáticos (Android 13) e pin das notificações");
            g.FillRectangle(claro, 24, 560, 1132, 196);
            // Glifo = só o primeiro plano (a silhueta), tingido com a cor do tema, sobre o círculo
            byte[] glifo = Recortar(MontarPrimeiroPlano(432), 432, 72, 72, 288);
            byte[] claroTema = (byte[])glifo.Clone(), escuroTema = (byte[])glifo.Clone();
            for (int i = 0; i < claroTema.Length; i += 4)
            {
                int a = claroTema[i + 3];
                claroTema[i] = (byte)(0x40 * a / 255); claroTema[i + 1] = (byte)(0x40 * a / 255); claroTema[i + 2] = (byte)(0x46 * a / 255);
                escuroTema[i] = (byte)(0xEA * a / 255); escuroTema[i + 1] = (byte)(0xEA * a / 255); escuroTema[i + 2] = (byte)(0xEA * a / 255);
            }
            g.FillEllipse(new SolidBrush(Color.FromArgb(0xE3, 0xE3, 0xE8)), 48, 576, 160, 160);
            Desenhar(g, claroTema, 288, 48, 576, 160);
            g.FillEllipse(new SolidBrush(Color.FromArgb(0x20, 0x20, 0x24)), 232, 576, 160, 160);
            Desenhar(g, escuroTema, 288, 232, 576, 160);
            Rotulo(g, texto, cinza, 48, 738, "tema claro");
            Rotulo(g, texto, cinza, 232, 738, "tema escuro");

            g.FillRectangle(escuro, 448, 600, 128, 128);
            Pin(g, new Rectangle(464, 616, 96, 96), Color.White, Color.FromArgb(0x1B, 0x1B, 0x22));
            g.FillRectangle(Brushes.White, 608, 600, 128, 128);
            Pin(g, new Rectangle(656, 648, 24, 24), Color.FromArgb(0x1B, 0x1B, 0x22), Color.White);
            g.FillRectangle(escuro, 784, 600, 128, 128);
            Pin(g, new Rectangle(832, 648, 24, 24), Color.White, Color.FromArgb(0x1B, 0x1B, 0x22));
            Rotulo(g, texto, cinza, 452, 738, "pin ampliado (4x)");
            Rotulo(g, texto, cinza, 604, 738, "tamanho real (24dp)");
            Rotulo(g, texto, cinza, 790, 738, "no escuro");

            folha.Save(caminho, ImageFormat.Png);
        }
    }
}
"@

if (-not (Test-Path -LiteralPath $Origem)) { throw "Arte-mestre não encontrada: $Origem" }
$Res = (Resolve-Path -LiteralPath $Res).Path
$utf8 = New-Object System.Text.UTF8Encoding($false)
$gravados = New-Object System.Collections.Generic.List[string]

function Garantir-Pasta([string]$caminho) {
    $pasta = Split-Path $caminho -Parent
    if (-not (Test-Path -LiteralPath $pasta)) { New-Item -ItemType Directory -Path $pasta | Out-Null }
}

function Gravar-Png([string]$caminho, [byte[]]$png) {
    Garantir-Pasta $caminho
    [System.IO.File]::WriteAllBytes($caminho, $png)
    $gravados.Add($caminho)
}

function Gravar-Xml([string]$caminho, [string]$xml) {
    Garantir-Pasta $caminho
    [System.IO.File]::WriteAllText($caminho, $xml.TrimEnd(), $utf8)
    $gravados.Add($caminho)
}

[GeradorIcone]::Preparar((Resolve-Path -LiteralPath $Origem).Path)
Write-Host ([GeradorIcone]::Relatorio())

# Uma pasta por densidade: ícone do Android 7 (48dp) e primeiro plano do adaptativo (108dp)
$densidades = [ordered]@{ mdpi = 1; hdpi = 1.5; xhdpi = 2; xxhdpi = 3; xxxhdpi = 4 }
foreach ($d in $densidades.GetEnumerator()) {
    $pasta = Join-Path $Res "mipmap-$($d.Key)"
    Gravar-Png (Join-Path $pasta 'ic_launcher.png') ([GeradorIcone]::Quadrado([int](48 * $d.Value)))
    Gravar-Png (Join-Path $pasta 'ic_launcher_round.png') ([GeradorIcone]::Redondo([int](48 * $d.Value)))
    Gravar-Png (Join-Path $pasta 'ic_launcher_foreground.png') ([GeradorIcone]::PrimeiroPlano([int](108 * $d.Value)))
    # Remove o ícone padrão do Android Studio que estava no lugar
    Get-ChildItem -LiteralPath $pasta -Filter 'ic_launcher*.webp' | ForEach-Object {
        Remove-Item -LiteralPath $_.FullName
        Write-Host "removido: $($_.FullName)"
    }
}

Gravar-Png (Join-Path $Res 'drawable-xxxhdpi\ic_launcher_monochrome.png') ([GeradorIcone]::Monocromatico(432))
# Ícone da loja (512x512) não pode ficar em res\ (o hífen é inválido em nome de recurso);
# fica na pasta art\ junto da arte-mestre
Gravar-Png (Join-Path $PSScriptRoot 'icone-playstore-512.png') ([GeradorIcone]::Loja(512))

Gravar-Xml (Join-Path $Res 'drawable\ic_launcher_gradient.xml') @"
<?xml version="1.0" encoding="utf-8"?>
<!-- Fundo do ícone: o gradiente de amarelo da arte-mestre (art/gerar-icone.ps1) -->
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient
        android:angle="270"
        android:startColor="$([GeradorIcone]::CorTopo())"
        android:endColor="$([GeradorIcone]::CorBase())"
        android:type="linear" />
</shape>
"@

Gravar-Xml (Join-Path $Res 'values\ic_launcher_background.xml') @"
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Amarelo do meio do gradiente do ícone; usado no splash do sistema (Android 12+) -->
    <color name="ic_launcher_background">$([GeradorIcone]::CorMedia())</color>
</resources>
"@

$iconeAdaptativo = @"
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_gradient"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>
</adaptive-icon>
"@
Gravar-Xml (Join-Path $Res 'mipmap-anydpi-v26\ic_launcher.xml') $iconeAdaptativo
Gravar-Xml (Join-Path $Res 'mipmap-anydpi-v26\ic_launcher_round.xml') $iconeAdaptativo

[GeradorIcone]::Folha($Folha)

Write-Host ""
Write-Host "gravados ($($gravados.Count)):"
$gravados | ForEach-Object { Write-Host "  $_" }
Write-Host "folha de conferência: $Folha"
