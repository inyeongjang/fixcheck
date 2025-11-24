## FixCheck-Enhanced: Context-Aware Assertion Generation for APCA

This repository is a research-oriented fork of the original FixCheck project developed by the IMDEA Software Institute. The primary goal of this fork is to extend FixCheck's assertion generation module by incorporating LLM-based, context-aware reasoning to enhance its complementary performance in Automated Patch Correctness Assessment (APCA).

## 🏛 Origin and License

This project is a fork of FixCheck (https://github.com/facumolina/fixcheck)

```
Copyright (c) 2024
IMDEA Software Institute
Licensed under the MIT License.
```

## 🔍 Differences from the Original FixCheck

This fork modifies and extends the following major components:

1. Assertion Generators
Added new LLM-based assertion generators
- OpenAI_GPT_New
- CodeLlama_7B_New, CodeLlama_13B_New
- Llama_Ollama_New
with constraints such as
- assertion-only output
- no undefined identifiers
- safe fallback assertions
- error location & root-cause–aware generation

2. Prompt Engineering Framework
Replaces simple prompts with structured, constrained templates and context injection.

3. Context Metadata Integration
Integration of `ROOT_CAUSE` & `ERROR_LOCATION` into the assertion-generation prompt.

## ⚙️ Installation & Usage

1. Clone this fork
```
git clone https://github.com/<your-username>/fixcheck-enhanced
cd fixcheck-enhanced
```

2. Set the required environment variables
```
export OPENAI_API_KEY=...
export OPENAI_MODEL=gpt-4o-mini
```

3. Run FixCheck with a chosen generator:

```
./run_fixcheck.sh --assertion-generation gpt-new
```

## 📚 Citation / Academic Use

If you use this fork for academic or experimental purposes, please cite:

```
@inproceedings{Molina2024FixCheck,
  author    = {Facundo Molina and Juan Manuel Copia and Alessandra Gorla},
  title     = {Improving Patch Correctness Analysis via Random Testing and Large Language Models},
  booktitle = {International Conference on Information Control Systems \& Technologies},
  year      = {2024},
  url       = {https://github.com/facumolina/fixcheck},
}
```

If you publish research results based on this fork,
please credit both the original FixCheck authors, and your fork (this repository)

## 🙏 Acknowledgements

Special thanks to the original FixCheck team at IMDEA Software Institute for the foundation of this work.
