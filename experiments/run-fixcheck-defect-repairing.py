import os
import sys
import subprocess
import pandas as pd
import re  # [ADDED] For parsing bug metadata (root cause / error location)

# Config variables
FIXCHECK = os.getenv('FIXCHECK')
DEFECT_REPAIRING_DATASET = os.getenv('DEFECT_REPAIRING_DATASET')

dataset_csv = 'experiments/defect-repairing-subjects.csv'
outputs_dir = 'fixcheck-output'
bug_info_dir = '/root/defects4j_bug_info'  # [ADDED] Path to Defects4J bug info files

# Get the arguments
subject_id = sys.argv[1]
assertion_generation = sys.argv[2]  # One of no-assertion, previous-assertion or llm-assertion
print(f'Running FixCheck for subject: {subject_id}')
print(f'assertion generation: {assertion_generation}')
df = pd.read_csv(dataset_csv)
subject_data = df[df['id'] == subject_id]

# Get and setup the subject data
project = subject_data['project'].values[0]
bug = subject_data['bug'].values[0]
subject_base_dir = os.path.join('/tmp', subject_data['base_dir'].values[0])

patch_base_dir = project + str(bug) + "b"
subject_base_dir = os.path.join(DEFECT_REPAIRING_DATASET, f'tmp/{subject_id}/{patch_base_dir}')


# [ADDED] Extract root cause and error location metadata from Defects4J bug info
def extract_bug_metadata(project, bug):
    info_file = os.path.join(bug_info_dir, f"{project.lower()}_bug_info.txt")
    root_cause, error_location = "", ""
    if os.path.exists(info_file):
        with open(info_file, "r") as f:
            text = f.read()
        # find section for this bug
        pattern = rf"Summary for Bug: {project}-{bug}[\s\S]*?List of modified sources:"
        match = re.search(pattern, text)
        if match:
            section = match.group()
            rc_match = re.search(r"Root cause.*?:([\s\S]*?)List of modified sources", section)
            if rc_match:
                root_cause = rc_match.group(1).strip().replace("\n", " ")
            # [ADDED] First modified source line is used as coarse error location
            el_match = re.search(r"- ([\w\./]+)", section)
            if el_match:
                error_location = el_match.group(1).strip()
    return root_cause, error_location


# [ADDED] Load metadata and expose it via environment variables (for LLM assertions)
root_cause, error_location = extract_bug_metadata(project, bug)
os.environ["ROOT_CAUSE"] = root_cause
os.environ["ERROR_LOCATION"] = error_location
print(f"→ Root cause: {root_cause}")
print(f"→ Error location: {error_location}")


def build_classpath(subject_base_dir, main_dep, test_classes_path):
    # Split main dep in char ':' and join each part with subject_base_dir
    subject_cp = ':'.join([os.path.join(subject_base_dir, dep) for dep in main_dep.split(':')])
    subject_cp = subject_cp + ':' + test_classes_path
    return subject_cp


# Dependencies
main_dep = subject_data['main_dep'].values[0]
test_classes = subject_data['tests_build'].values[0]
test_classes_path = os.path.join(subject_base_dir, test_classes)
subject_cp = build_classpath(subject_base_dir, main_dep, test_classes_path)
# Classes and methods
target_test = subject_data['target_test'].values[0]
target_test_methods = subject_data['target_test_methods'].values[0]
tests_src_dir = subject_data['tests_src_dir'].values[0]
target_test_dir = os.path.join(subject_base_dir, tests_src_dir)
target_class = subject_data['target_class'].values[0]
input_class = subject_data['input_class'].values[0]
failure_log = os.path.join(subject_base_dir, 'failing_tests')

# [ADDED] Prepare a copy of the environment with ROOT_CAUSE / ERROR_LOCATION for FixCheck + LLM
env = os.environ.copy()
env["ROOT_CAUSE"] = root_cause
env["ERROR_LOCATION"] = error_location

# Run FixCheck
subprocess.run(
    f'./fixcheck.sh {subject_cp} {test_classes_path} {target_test} {target_test_methods} '
    f'{target_test_dir} {target_class} {input_class} {failure_log} {assertion_generation}',
    shell=True,
    env=env,  # [CHANGED] Pass enriched environment so the Java side can read bug metadata
)

# Move all outputs to a folder specific to the current subject
output_file = os.path.join(outputs_dir, subject_id + '-report.csv')
subject_output_folder = os.path.join(outputs_dir, f'defects-repairing/{subject_id}/{assertion_generation}')
print(f'Moving all outputs to dir: {subject_output_folder}')
if not os.path.exists(subject_output_folder):
    os.makedirs(subject_output_folder)
subprocess.run(f'mv {outputs_dir}/report.csv {subject_output_folder}', shell=True)
subprocess.run(f'mv {outputs_dir}/scores-failing-tests.csv {subject_output_folder}', shell=True)
subprocess.run(f'mv {outputs_dir}/failing-tests {subject_output_folder}', shell=True)
subprocess.run(f'mv {outputs_dir}/passing-tests {subject_output_folder}', shell=True)
subprocess.run(f'mv {outputs_dir}/non-compiling-tests {subject_output_folder}', shell=True)
subprocess.run(f'mv log.out {subject_output_folder}', shell=True)
