import sys
import os
import pandas as pd

RESULTS_ROOT = "fixcheck-output/defects-repairing"
DATASET_CSV = "experiments/defect-repairing-subjects.csv"
THRESHOLD = 0.40 

if len(sys.argv) < 2:
    print("Usage: python analyze_fixcheck_outcomes_v2.py <assertion_generation>")
    sys.exit(1)
assertion_generation = sys.argv[1]

target_ids = [
    1, 2, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
    21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 36, 37, 38,
    44, 45, 46, 47, 48, 49, 51, 53, 54, 55, 58, 59, 62, 63, 64, 65, 66,
    67, 68, 69, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 88,
    89, 90, 91, 92, 93, 150, 151, 152, 153, 154, 155, 157, 158, 159,
    160, 161, 162, 163, 165, 166, 167, 168, 169, 170, 171, 172, 173,
    174, 175, 176, 177, 180, 181, 182, 183, 184, 185, 186, 187, 188,
    189, 191, 192, 193, 194, 195, 196, 197, 198, 199, 201, 202, 203,
    204, 205, 206, 207, 208, 209, 210,
    "HDRepair1", "HDRepair3", "HDRepair4", "HDRepair5", "HDRepair6",
    "HDRepair7", "HDRepair8", "HDRepair9", "HDRepair10"
]
target_patches = ["Patch" + str(i) for i in target_ids]

subjects_df = pd.read_csv(DATASET_CSV)

columns = [
    "project",
    "patch_id",
    "correctness",
    "total_tests",
    "passing_tests",
    "non_compiling_tests",
    "assertion_failing_tests",
    "crashing_tests",
    "passing_ratio",
    "non_compiling_ratio",
    "assertion_failing_ratio",
    "crashing_ratio",
    
    # assertion-failing score 
    "af_score_count",
    "af_score_over_count",
    "af_score_sum",
    "af_score_over_sum",
    "af_score_mean",
    "af_score_over_mean",
    
    # crashing score 
    "crash_score_count",
    "crash_score_over_count",
]
results_df = pd.DataFrame(columns=columns)

missing_reports = []

for patch_id in target_patches:
    subject_row = subjects_df[subjects_df["id"] == patch_id]
    if subject_row.empty:
        print(f"[WARN] {patch_id} not found in {DATASET_CSV}")
        continue

    project = subject_row["project"].values[0]
    correctness = subject_row["correctness"].values[0]

    if correctness == "Correct":
        continue

    base_dir = os.path.join(RESULTS_ROOT, patch_id, assertion_generation)
    report_path = os.path.join(base_dir, "report.csv")
    scores_path = os.path.join(base_dir, "scores-failing-tests.csv")

    total_tests = passing_tests = non_compiling_tests = 0
    assertion_failing_tests = crashing_tests = 0
    passing_ratio = non_compiling_ratio = 0.0
    assertion_failing_ratio = crashing_ratio = 0.0

    af_score_count = af_score_over_count = 0
    af_score_sum = af_score_over_sum = 0.0
    af_score_mean = af_score_over_mean = None

    crash_score_count = crash_score_over_count = 0

    if os.path.exists(report_path):
        report = pd.read_csv(report_path)

        total_tests = int(report["output_prefixes"].sum())
        passing_tests = int(report["passing_prefixes"].sum())
        crashing_tests = int(report["crashing_prefixes"].sum())
        assertion_failing_tests = int(report["assertion_failing_prefixes"].sum())
        non_compiling_tests = total_tests - (
            passing_tests + crashing_tests + assertion_failing_tests
        )
        if non_compiling_tests < 0:
            non_compiling_tests = 0

        if total_tests > 0:
            passing_ratio = round(passing_tests / total_tests * 100, 2)
            non_compiling_ratio = round(non_compiling_tests / total_tests * 100, 2)
            assertion_failing_ratio = round(
                assertion_failing_tests / total_tests * 100, 2
            )
            crashing_ratio = round(crashing_tests / total_tests * 100, 2)

        if os.path.exists(scores_path):
            scores_df = pd.read_csv(scores_path)

            af_scores = []
            crash_scores = []

            for _, row in scores_df.iterrows():
                prefix = str(row["prefix"])
                score = float(row["score"])
                if "with" in prefix:
                    af_scores.append(score)      # assertion-failing
                else:
                    crash_scores.append(score)   # crashing (혹은 기타 failing)

            af_score_count = len(af_scores)
            if af_score_count > 0:
                af_score_sum = sum(af_scores)
                af_score_mean = af_score_sum / af_score_count

                af_over = [s for s in af_scores if s >= THRESHOLD]
                af_score_over_count = len(af_over)
                if af_score_over_count > 0:
                    af_score_over_sum = sum(af_over)
                    af_score_over_mean = af_score_over_sum / af_score_over_count

            crash_score_count = len(crash_scores)
            if crash_score_count > 0:
                crash_over = [s for s in crash_scores if s >= THRESHOLD]
                crash_score_over_count = len(crash_over)

            if assertion_failing_tests != 0 and af_score_count != assertion_failing_tests:
                print(
                    f"[WARN] {patch_id}: assertion_failing_tests={assertion_failing_tests} "
                    f"but af_score_count={af_score_count} (prefix 분류와 report.csv 불일치 가능)"
                )

    else:
        print(f"[WARN] report.csv not found for {patch_id} ({base_dir})")
        missing_reports.append(patch_id)

    new_row = {
        "project": project,
        "patch_id": patch_id,
        "correctness": correctness,
        "total_tests": total_tests,
        "passing_tests": passing_tests,
        "non_compiling_tests": non_compiling_tests,
        "assertion_failing_tests": assertion_failing_tests,
        "crashing_tests": crashing_tests,
        "passing_ratio": passing_ratio,
        "non_compiling_ratio": non_compiling_ratio,
        "assertion_failing_ratio": assertion_failing_ratio,
        "crashing_ratio": crashing_ratio,
        "af_score_count": af_score_count,
        "af_score_over_count": af_score_over_count,
        "af_score_sum": af_score_sum,
        "af_score_over_sum": af_score_over_sum,
        "af_score_mean": af_score_mean,
        "af_score_over_mean": af_score_over_mean,
        "crash_score_count": crash_score_count,
        "crash_score_over_count": crash_score_over_count,
    }
    results_df = pd.concat([results_df, pd.DataFrame([new_row])], ignore_index=True)

    if total_tests > 0:
        print(f"\n[Patch] {patch_id} (project={project}, correctness={correctness})")
        print(f"  total tests        : {total_tests}")
        print(f"  passing            : {passing_tests} ({passing_ratio:.2f}%)")
        print(f"  non-compiling      : {non_compiling_tests} ({non_compiling_ratio:.2f}%)")
        print(f"  assertion-failing  : {assertion_failing_tests} ({assertion_failing_ratio:.2f}%)")
        print(f"  crashing           : {crashing_tests} ({crashing_ratio:.2f}%)")

        if af_score_count > 0:
            over_ratio = af_score_over_count / af_score_count * 100 if af_score_count > 0 else 0.0
            print(
                f"  [assertion-failing scores] count={af_score_count}, "
                f"mean={af_score_mean:.4f} "
            )
            print(
                f"    >= {THRESHOLD:.2f}: {af_score_over_count} ({over_ratio:.2f}%)",
                end=""
            )
            if af_score_over_count > 0:
                print(f", mean(over)={af_score_over_mean:.4f}")
            else:
                print("")
        else:
            print("  [assertion-failing scores] none")

        if crash_score_count > 0:
            crash_over_ratio = (
                crash_score_over_count / crash_score_count * 100
                if crash_score_count > 0
                else 0.0
            )
            print(
                f"  [crashing scores] count={crash_score_count}, "
                f">= {THRESHOLD:.2f}: {crash_score_over_count} ({crash_over_ratio:.2f}%)"
            )
        else:
            print("  [crashing scores] none")

def summarize_project(df, project_name):
    if project_name == "TOTAL":
        proj_df = df
    else:
        proj_df = df[df["project"] == project_name]

    if proj_df.empty:
        return

    total_patches = len(proj_df)
    total_tests = int(proj_df["total_tests"].sum())
    total_pass = int(proj_df["passing_tests"].sum())
    total_nc = int(proj_df["non_compiling_tests"].sum())
    total_af = int(proj_df["assertion_failing_tests"].sum())
    total_crash = int(proj_df["crashing_tests"].sum())

    pass_ratio = total_pass / total_tests * 100 if total_tests > 0 else 0.0
    nc_ratio = total_nc / total_tests * 100 if total_tests > 0 else 0.0
    af_ratio = total_af / total_tests * 100 if total_tests > 0 else 0.0
    crash_ratio = total_crash / total_tests * 100 if total_tests > 0 else 0.0

    print(f"\n=== {project_name} ===")
    print(f" #patches           : {total_patches}")
    print(f" total tests        : {total_tests}")
    print(f" passing            : {total_pass} ({pass_ratio:.2f}%)")
    print(f" non-compiling      : {total_nc} ({nc_ratio:.2f}%)")
    print(f" assertion-failing  : {total_af} ({af_ratio:.2f}%)")
    print(f" crashing           : {total_crash} ({crash_ratio:.2f}%)")

    total_af_score_sum = proj_df["af_score_sum"].sum()
    total_af_score_count = proj_df["af_score_count"].sum()
    total_af_over_sum = proj_df["af_score_over_sum"].sum()
    total_af_over_count = proj_df["af_score_over_count"].sum()

    total_crash_score_count = proj_df["crash_score_count"].sum()
    total_crash_over_count = proj_df["crash_score_over_count"].sum()

    if total_af_score_count > 0:
        global_af_mean = total_af_score_sum / total_af_score_count
        print(f" assertion-failing score mean (all): {global_af_mean:.4f}")
    else:
        print(" assertion-failing score mean (all): N/A")

    if total_af_over_count > 0:
        global_af_over_mean = total_af_over_sum / total_af_over_count
        print(f" assertion-failing score mean (>= {THRESHOLD:.2f}): {global_af_over_mean:.4f}")
    else:
        print(f" assertion-failing score mean (>= {THRESHOLD:.2f}): N/A")

    if total_af_score_count > 0:
        af_over_ratio = total_af_over_count / total_af_score_count * 100
        print(
            f" assertion-failing score >= {THRESHOLD:.2f}: "
            f"{total_af_over_count}/{total_af_score_count} ({af_over_ratio:.2f}%)"
        )
    else:
        print(f" assertion-failing score >= {THRESHOLD:.2f}: N/A")

    if total_crash_score_count > 0:
        crash_over_ratio = total_crash_over_count / total_crash_score_count * 100
        print(
            f" crashing score >= {THRESHOLD:.2f}: "
            f"{total_crash_over_count}/{total_crash_score_count} ({crash_over_ratio:.2f}%)"
        )
    else:
        print(f" crashing score >= {THRESHOLD:.2f}: N/A")

print("\n\n[Project-level summary]")
for proj in ["Chart", "Lang", "Math", "Time"]:
    summarize_project(results_df, proj)
summarize_project(results_df, "TOTAL")

if missing_reports:
    print("\n[WARN] report.csv not found for the following patches (excluded from detailed test stats):")
    print(" ", " ".join(missing_reports))
else:
    print("\n[INFO] All analyzed incorrect patches had report.csv.")
