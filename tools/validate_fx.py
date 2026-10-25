"""Validates Blocmpact effect shaders outside the game.

Assembles every assets/<ns>/fx/**/*.fsh exactly like ShaderProgram.assemble does at runtime ("#version 150" +
blocmpact:lib/core.glsl + the program, #include expanded once, #line directives) and compiles it with glslang
(https://github.com/KhronosGroup/glslang/releases). Errors are reported as <namespace>:<file>:<line>.

usage:
  python tools/validate_fx.py <path/to/glslang> <assets-root> [<assets-root> ...] [--extra some_test.fsh ...]

  <assets-root> is a folder containing namespaces (e.g. src/client/resources/assets). Pass Blocmpact's own assets
  root too when validating another mod, so blocmpact:lib/*.glsl can be found.
"""
import os
import re
import subprocess
import sys
import tempfile

INCLUDE = re.compile(r'^\s*#\s*include\s+["<]([^">]+)[">]\s*$')


def resolve(roots, ns, path):
    for r in roots:
        p = os.path.join(r, ns, 'fx', *path.split('/'))
        if os.path.exists(p):
            return p
    raise FileNotFoundError(f'{ns}:fx/{path}')


def append_lines(out, roots, ns, lines, idx, files, included):
    out.append(f'#line 0 {idx}')
    for i, line in enumerate(lines):
        m = INCLUDE.match(line)
        if m:
            ref = m.group(1).strip()
            ins, ipath = ref.split(':', 1) if ':' in ref else (ns, ref)
            append(out, roots, ins, ipath, files, included)
            out.append(f'#line {i + 1} {idx}')
        elif line.strip().startswith('#version'):
            out.append('// ' + line)
        else:
            out.append(line)


def append(out, roots, ns, path, files, included):
    key = f'{ns}:{path}'
    if key in included:
        return
    included.add(key)
    files.append(key)
    with open(resolve(roots, ns, path), encoding='utf-8') as f:
        lines = f.read().split('\n')
    append_lines(out, roots, ns, lines, len(files), files, included)


def assemble(roots, ns, program, extra_path=None):
    out = ['#version 150']
    files, included = [], set()
    append(out, roots, 'blocmpact', 'lib/core.glsl', files, included)
    if extra_path:
        files.append(extra_path)
        with open(extra_path, encoding='utf-8') as f:
            lines = f.read().split('\n')
        append_lines(out, roots, 'blocmpact', lines, len(files), files, included)
    else:
        append(out, roots, ns, program + '.fsh', files, included)
    return '\n'.join(out) + '\n', files


def main():
    glslang = sys.argv[1]
    roots, extras = [], []
    args = sys.argv[2:]
    i = 0
    while i < len(args):
        if args[i] == '--extra':
            extras.append(args[i + 1])
            i += 2
        else:
            roots.append(args[i])
            i += 1
    programs = []
    for r in roots:
        for ns in sorted(os.listdir(r)):
            fx = os.path.join(r, ns, 'fx')
            for dp, _, fns in os.walk(fx):
                for fn in sorted(fns):
                    if fn.endswith('.fsh'):
                        rel = os.path.relpath(os.path.join(dp, fn), fx).replace('\\', '/')[:-4]
                        programs.append((ns, rel, None))
    for e in extras:
        programs.append(('extra', os.path.basename(e), e))
    failures = 0
    for ns, prog, extra in programs:
        src, files = assemble(roots, ns, prog, extra_path=extra)
        with tempfile.NamedTemporaryFile('w', suffix='.frag', delete=False, encoding='utf-8') as t:
            t.write(src)
            name = t.name
        r = subprocess.run([glslang, '-S', 'frag', name], capture_output=True, text=True)
        os.unlink(name)
        out = (r.stdout + r.stderr).strip()
        ok = r.returncode == 0

        def tr(m):
            k = int(m.group(1))
            return f'{files[k - 1]}:{m.group(2)}' if 1 <= k <= len(files) else m.group(0)

        out = re.sub(r'(?<![\w.])(\d+):(\d+)', tr, out)
        print(('OK   ' if ok else 'FAIL ') + f'{ns}:{prog}')
        if not ok:
            failures += 1
            print('   ' + out.replace('\n', '\n   '))
    print(f'{len(programs) - failures}/{len(programs)} programs valid')
    sys.exit(1 if failures else 0)


if __name__ == '__main__':
    main()
