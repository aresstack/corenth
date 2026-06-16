package com.aresstack.corenth.proasteion.emporion.holkas.mvs;

public final class MvsLocation {

    private final MvsLocationType type;
    private final String logicalPath;
    private final String displayName;

    private MvsLocation(MvsLocationType type, String logicalPath, String displayName) {
        this.type = type;
        this.logicalPath = logicalPath;
        this.displayName = displayName;
    }

    public static MvsLocation root() {
        return new MvsLocation(MvsLocationType.ROOT, "", "");
    }

    public static MvsLocation hlq(String hlq) {
        String logical = MvsQuoteNormalizer.normalize(hlq);
        return new MvsLocation(MvsLocationType.HLQ, logical, MvsQuoteNormalizer.unquote(logical));
    }

    public static MvsLocation qualifierContext(String path) {
        String logical = MvsQuoteNormalizer.normalize(path);
        return new MvsLocation(MvsLocationType.QUALIFIER_CONTEXT, logical,
                lastQualifier(MvsQuoteNormalizer.unquote(logical)));
    }

    public static MvsLocation dataset(String datasetName) {
        String logical = MvsQuoteNormalizer.normalize(datasetName);
        return new MvsLocation(MvsLocationType.DATASET, logical,
                lastQualifier(MvsQuoteNormalizer.unquote(logical)));
    }

    public static MvsLocation member(String memberPath) {
        String logical = MvsQuoteNormalizer.normalize(memberPath);
        return new MvsLocation(MvsLocationType.MEMBER, logical,
                MvsMemberLocator.parse(logical).memberName());
    }

    public static MvsLocation parse(String path) {
        if (path == null || path.trim().isEmpty()) {
            return root();
        }
        String logical = MvsQuoteNormalizer.normalize(path);
        String unquoted = MvsQuoteNormalizer.unquote(logical);
        if (unquoted.isEmpty()) {
            return root();
        }
        if (unquoted.indexOf('(') >= 0 && unquoted.indexOf(')') >= 0) {
            return member(unquoted);
        }
        if (unquoted.indexOf('.') >= 0) {
            return qualifierContext(unquoted);
        }
        return hlq(unquoted);
    }

    public MvsLocation createChild(String childName) {
        if (childName == null || childName.trim().isEmpty()) {
            return this;
        }
        String child = MvsQuoteNormalizer.unquote(childName.trim());
        String parent = MvsQuoteNormalizer.unquote(logicalPath);
        if (type == MvsLocationType.ROOT) {
            return hlq(child);
        }
        if (type == MvsLocationType.HLQ || type == MvsLocationType.QUALIFIER_CONTEXT) {
            if (child.toUpperCase().startsWith(parent.toUpperCase() + ".")) {
                return qualifierContext(child);
            }
            return qualifierContext(parent + "." + child);
        }
        if (type == MvsLocationType.DATASET) {
            if (child.indexOf('.') < 0 && child.length() <= 8) {
                return member(parent + "(" + child + ")");
            }
            if (child.toUpperCase().startsWith(parent.toUpperCase() + ".")) {
                return dataset(child);
            }
            return dataset(parent + "." + child);
        }
        return this;
    }

    public String queryPath() {
        if (type == MvsLocationType.ROOT) {
            return "''";
        }
        if (type == MvsLocationType.HLQ || type == MvsLocationType.QUALIFIER_CONTEXT) {
            return MvsQuoteNormalizer.wildcardQuery(logicalPath);
        }
        return logicalPath;
    }

    public boolean isDirectory() {
        return type == MvsLocationType.ROOT
                || type == MvsLocationType.HLQ
                || type == MvsLocationType.QUALIFIER_CONTEXT
                || type == MvsLocationType.DATASET;
    }

    public MvsLocationType type() {
        return type;
    }

    public String logicalPath() {
        return logicalPath;
    }

    public String displayName() {
        return displayName;
    }

    private static String lastQualifier(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 && lastDot < path.length() - 1 ? path.substring(lastDot + 1) : path;
    }
}
