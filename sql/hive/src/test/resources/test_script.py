import sys

if __name__ == "__main__":
    line = ''
    try:
        for line in sys.stdin:
            col1, col2, col3 = line.strip().split('\t')
            print "%s\t%s_%s" % (col1, col2, col3)

    except:
        sys.exit(1)
