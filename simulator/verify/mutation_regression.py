#!/usr/bin/env python3
import subprocess
import sys
import os

ALU_PATH = "src/cpu/ALU.java"

def main():
    with open(ALU_PATH, "r") as f:
        original = f.read()

    # Mutation: change ADD case result to 1
    mutation = original.replace(
        "            case ADD -> {\n                int full = operand1 + operand2;\n                result = full & MASK;",
        "            case ADD -> {\n                int full = operand1 + operand2;\n                result = 1; // MUTATION SANITY TEST"
    )

    with open(ALU_PATH, "w") as f:
        f.write(mutation)

    print("=== Applying mutation ===")

    # Find all java files excluding gui
    java_files = []
    for root, dirs, files in os.walk("src"):
        if "gui" in root:
            continue
        for file in files:
            if file.endswith(".java"):
                java_files.append(os.path.join(root, file))

    # Compile mutation
    subprocess.run(
        [
            "C:/Program Files/Java/jdk-25.0.2/bin/javac",
            "-sourcepath", "src",
            "-d", "C:/tmp/mut_full"
        ] + java_files,
        capture_output=True,
        text=True,
        check=False
    )

    # Compile property test runner against mutation
    subprocess.run(
        [
            "C:/Program Files/Java/jdk-25.0.2/bin/javac",
            "-sourcepath", "src",
            "-d", "C:/tmp/mut_full",
            "C:/tmp/RunPropertyTest.java"
        ],
        capture_output=True,
        text=True,
        check=False
    )

    # Find .m2 jar classpath
    import glob
    jars = []
    for jar_file in glob.glob("C:/Users/LENOVO/.m2/repository/**/*.jar", recursive=True):
        if "sources" not in jar_file and "javadoc" not in jar_file and "win-" not in jar_file:
            jars.append(jar_file)
    classpath = ";".join(jars)

    # Run property test with mutation
    result_mut = subprocess.run(
        [
            "C:/Program Files/Java/jdk-25.0.2/bin/java",
            "-cp", "C:/tmp/mut_full;" + classpath,
            "RunPropertyTest"
        ],
        capture_output=True,
        text=True,
        check=False
    )

    mutation_exit = result_mut.returncode
    mutation_output = result_mut.stdout + result_mut.stderr
    print("=== Mutation property test ===")
    print("Exit code:", mutation_exit)
    for line in mutation_output.splitlines()[-10:]:
        print("  ", line)

    # Restore original ALU
    with open(ALU_PATH, "w") as f:
        f.write(original)
    print("=== Restored original ALU ===")

    # Compile clean
    subprocess.run(
        [
            "C:/Program Files/Java/jdk-25.0.2/bin/javac",
            "-sourcepath", "src",
            "-d", "C:/tmp/clean_full"
        ] + java_files,
        capture_output=True,
        text=True,
        check=False
    )

    subprocess.run(
        [
            "C:/Program Files/Java/jdk-25.0.2/bin/javac",
            "-sourcepath", "src",
            "-d", "C:/tmp/clean_full",
            "C:/tmp/RunPropertyTest.java"
        ],
        capture_output=True,
        text=True,
        check=False
    )

    result_clean = subprocess.run(
        [
            "C:/Program Files/Java/jdk-25.0.2/bin/java",
            "-cp", "C:/tmp/clean_full;" + classpath,
            "RunPropertyTest"
        ],
        capture_output=True,
        text=True,
        check=False
    )

    clean_exit = result_clean.returncode
    clean_output = result_clean.stdout + result_clean.stderr
    print("=== Clean property test ===")
    print("Exit code:", clean_exit)
    for line in clean_output.splitlines()[-10:]:
        print("  ", line)

    if mutation_exit != 0 and clean_exit == 0:
        print("\nMUTATION REGRESSION TEST: SUCCESS")
        return 0
    else:
        print("\nMUTATION REGRESSION TEST: FAILURE (mutation exit=" + str(mutation_exit) + ", clean exit=" + str(clean_exit) + ")")
        return 1

if __name__ == "__main__":
    sys.exit(main())
