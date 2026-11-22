package pl.q50.app.data;

import pl.q50.app.i18n.Strings;

/** How to turn one raw sensor value into a labelled, scaled vehicle signal. */
public final class SignalDef {
    public final int type;
    public final String key;
    public final String labelPl;
    public final String labelEn;
    public final String unitPl;
    public final String unitEn;
    public final float scale;
    public final float offset;
    public final int decimals;
    public final boolean verified;
    /** "gear" for the gear enum; null for plain numbers. */
    public final String format;
    /** Value below which the signal is shown as "cold" (NaN = no threshold). */
    public final float cold;
    /** Value above which the signal is shown as "hot" (NaN = no threshold). */
    public final float hot;

    public SignalDef(int type, String key, String labelPl, String labelEn, String unitPl, String unitEn,
                     float scale, float offset, int decimals, boolean verified, String format, float cold, float hot) {
        this.type = type;
        this.key = key;
        this.labelPl = labelPl;
        this.labelEn = labelEn;
        this.unitPl = unitPl;
        this.unitEn = unitEn;
        this.scale = scale;
        this.offset = offset;
        this.decimals = decimals;
        this.verified = verified;
        this.format = format;
        this.cold = cold;
        this.hot = hot;
    }

    public String label() {
        return Strings.get().en ? labelEn : labelPl;
    }

    public String unit() {
        return Strings.get().en ? unitEn : unitPl;
    }

    public float convert(float raw) {
        return raw * scale + offset;
    }

    public String formatRaw(float raw) {
        if ("gear".equals(format)) {
            return gear(Math.round(raw));
        }
        return Fmt.number(convert(raw), decimals);
    }

    /** Observed on a VR30: P=1, R=2, N=3, D=4, manual M1..M7 = 16..22. */
    public static String gear(int raw) {
        switch (raw) {
            case 1: return "P";
            case 2: return "R";
            case 3: return "N";
            case 4: return "D";
            default:
                if (raw >= 16 && raw <= 22) {
                    return "M" + (raw - 15);
                }
                return "?" + raw;
        }
    }
}
