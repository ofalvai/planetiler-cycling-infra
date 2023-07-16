default:
    @just --list

jar_path := "target/*with-deps.jar"

clean-build-europe: build-planetiler
    java -Xmx20g \
        `# return unused heap memory to the OS` \
        -XX:MaxHeapFreeRatio=40 \
        -jar {{jar_path}} --force --download \
        --area=europe \
        --output=data/planetiler-europe-cycling.mbtiles \
        `# Store temporary node locations at fixed positions in a memory-mapped file` \
        --nodemap-type=array --storage=mmap \
        --config=config-cycling.properties

build AREA:
    java -jar {{jar_path}} \
        --area={{AREA}} \
        --output=data/planetiler-{{AREA}}-cycling.mbtiles \
        --nodemap-type=array --storage=mmap \
        --config=config-cycling.properties

build-planetiler:
    ./mvnw -DskipTests=true clean package

download AREA:
    java -jar {{jar_path}} --only-download --area={{AREA}}
