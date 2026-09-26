0x1407f8e80 lea r12, [rsp - 0x40]
0x1407f8e85 cmp r12, qword ptr [r14 + 0x10]
0x1407f8e89 jbe 0x1407f936d
0x1407f8e8f push rbp
0x1407f8e90 mov rbp, rsp
0x1407f8e93 sub rsp, 0xb8
0x1407f8e9a mov qword ptr [rsp + 0xd0], rbx
0x1407f8ea2 cmp rcx, 0x6e
0x1407f8ea6 jl 0x1407f9304
0x1407f8eac cmp byte ptr [rbx], 2
0x1407f8eaf je 0x1407f8f56
0x1407f8eb5 movups xmmword ptr [rsp + 0xa8], xmm15
0x1407f8ebe movzx edx, byte ptr [rbx]
0x1407f8ec1 lea r8, [rip + 0x15a3bf8]
0x1407f8ec8 mov qword ptr [rsp + 0xa8], r8
0x1407f8ed0 lea r8, [rip + 0xcaec49]
0x1407f8ed7 lea rdx, [r8 + rdx*8]
0x1407f8edb mov qword ptr [rsp + 0xb0], rdx
0x1407f8ee3 lea rax, [rip + 0x22dabb]
0x1407f8eea mov ebx, 0x26
0x1407f8eef lea rcx, [rsp + 0xa8]
0x1407f8ef7 mov edi, 1
0x1407f8efc mov esi, edi
0x1407f8efe nop 
0x1407f8f00 call 0x14013d080
0x1407f8f05 test rax, rax
0x1407f8f08 jne 0x1407f8f41
0x1407f8f0a nop 
0x1407f8f0b mov eax, 0x10
0x1407f8f10 lea rbx, [rip + 0x15c75e9]
0x1407f8f17 mov ecx, 1
0x1407f8f1c nop dword ptr [rax]
0x1407f8f20 call 0x140022a00
0x1407f8f25 mov qword ptr [rax + 8], 0x26
0x1407f8f2d lea rdx, [rip + 0x22da71]
0x1407f8f34 mov qword ptr [rax], rdx
0x1407f8f37 mov rbx, rax
0x1407f8f3a lea rax, [rip + 0x16879c7]
0x1407f8f41 xor ecx, ecx
0x1407f8f43 mov rdi, rax
0x1407f8f46 mov rsi, rbx
0x1407f8f49 xor eax, eax
0x1407f8f4b mov ebx, ecx
0x1407f8f4d add rsp, 0xb8
0x1407f8f54 pop rbp
0x1407f8f55 ret 
0x1407f8f56 mov qword ptr [rsp + 0xc8], rax
0x1407f8f5e mov qword ptr [rsp + 0xe0], rdi
0x1407f8f66 mov qword ptr [rsp + 0xd8], rcx
0x1407f8f6e mov qword ptr [rsp + 0xd0], rbx
0x1407f8f76 inc rbx
0x1407f8f79 dec rdi
0x1407f8f7c mov rax, qword ptr [rip + 0x179b885]
0x1407f8f83 mov ecx, 0x41
0x1407f8f88 call 0x1401d1820
0x1407f8f8d test rbx, rbx
0x1407f8f90 je 0x1407f9023
0x1407f8f96 movups xmmword ptr [rsp + 0x98], xmm15
0x1407f8f9f nop 
0x1407f8fa0 je 0x1407f8fa6
0x1407f8fa2 mov rbx, qword ptr [rbx + 8]
0x1407f8fa6 mov qword ptr [rsp + 0x98], rbx
0x1407f8fae mov qword ptr [rsp + 0xa0], rcx
0x1407f8fb6 lea rax, [rip + 0x22da0e]
0x1407f8fbd mov ebx, 0x26
0x1407f8fc2 lea rcx, [rsp + 0x98]
0x1407f8fca mov edi, 1
0x1407f8fcf mov esi, edi
0x1407f8fd1 call 0x14013d080
0x1407f8fd6 test rax, rax
0x1407f8fd9 jne 0x1407f900e
0x1407f8fdb nop 
0x1407f8fdc mov eax, 0x10
0x1407f8fe1 lea rbx, [rip + 0x15c7518]
0x1407f8fe8 mov ecx, 1
0x1407f8fed call 0x140022a00
0x1407f8ff2 mov qword ptr [rax + 8], 0x26
0x1407f8ffa lea rdx, [rip + 0x22d9ca]
0x1407f9001 mov qword ptr [rax], rdx
0x1407f9004 mov rbx, rax
0x1407f9007 lea rax, [rip + 0x16878fa]
0x1407f900e xor ecx, ecx
0x1407f9010 mov rdi, rax
0x1407f9013 mov rsi, rbx
0x1407f9016 xor eax, eax
0x1407f9018 mov ebx, ecx
0x1407f901a add rsp, 0xb8
0x1407f9021 pop rbp
0x1407f9022 ret 
0x1407f9023 mov rcx, qword ptr [rsp + 0xc8]
0x1407f902b mov rcx, qword ptr [rcx]
0x1407f902e mov rbx, rax
0x1407f9031 mov rax, rcx
0x1407f9034 call 0x1401d0fc0
0x1407f9039 nop dword ptr [rax]
0x1407f9040 test rdi, rdi
0x1407f9043 je 0x1407f90d6
0x1407f9049 movups xmmword ptr [rsp + 0x88], xmm15
0x1407f9052 je 0x1407f9058
0x1407f9054 mov rdi, qword ptr [rdi + 8]
0x1407f9058 mov qword ptr [rsp + 0x88], rdi
0x1407f9060 mov qword ptr [rsp + 0x90], rsi
0x1407f9068 lea rax, [rip + 0x212ae6]
0x1407f906f mov ebx, 0x18
0x1407f9074 lea rcx, [rsp + 0x88]
0x1407f907c mov edi, 1
0x1407f9081 mov esi, edi
0x1407f9083 call 0x14013d080
0x1407f9088 test rax, rax
0x1407f908b jne 0x1407f90c1
0x1407f908d nop 
0x1407f908e mov eax, 0x10
0x1407f9093 lea rbx, [rip + 0x15c7466]
0x1407f909a mov ecx, 1
0x1407f909f nop 
0x1407f90a0 call 0x140022a00
0x1407f90a5 mov qword ptr [rax + 8], 0x18
0x1407f90ad lea rdx, [rip + 0x212aa1]
0x1407f90b4 mov qword ptr [rax], rdx
0x1407f90b7 mov rbx, rax
0x1407f90ba lea rax, [rip + 0x1687847]
0x1407f90c1 xor ecx, ecx
0x1407f90c3 mov rdi, rax
0x1407f90c6 mov rsi, rbx
0x1407f90c9 xor eax, eax
0x1407f90cb mov ebx, ecx
0x1407f90cd add rsp, 0xb8
0x1407f90d4 pop rbp
0x1407f90d5 ret 
0x1407f90d6 lea rdx, [rip + 0x205eb7]
0x1407f90dd mov qword ptr [rsp], rdx
0x1407f90e1 mov qword ptr [rsp + 8], 0x11
0x1407f90ea mov r8, qword ptr [rsp + 0xd0]
0x1407f90f2 add r8, 0x42
0x1407f90f6 mov r10, qword ptr [rsp + 0xe0]
0x1407f90fe add r10, -0x42
0x1407f9102 mov rdi, rbx
0x1407f9105 mov rsi, rcx
0x1407f9108 mov r9d, 0x10
0x1407f910e mov r11d, 0x20
0x1407f9114 lea rbx, [rip + 0x169762d]
0x1407f911b mov rcx, rax
0x1407f911e lea rax, [rip + 0xc89b7b]
0x1407f9125 call 0x14085fca0
0x1407f912a test rdi, rdi
0x1407f912d je 0x1407f91b6
0x1407f9133 movups xmmword ptr [rsp + 0x78], xmm15
0x1407f9139 je 0x1407f913f
0x1407f913b mov rdi, qword ptr [rdi + 8]
0x1407f913f mov qword ptr [rsp + 0x78], rdi
0x1407f9144 mov qword ptr [rsp + 0x80], rsi
0x1407f914c lea rax, [rip + 0x212a1a]
0x1407f9153 mov ebx, 0x18
0x1407f9158 lea rcx, [rsp + 0x78]
0x1407f915d mov edi, 1
0x1407f9162 mov esi, edi
0x1407f9164 call 0x14013d080
0x1407f9169 test rax, rax
0x1407f916c jne 0x1407f91a1
0x1407f916e nop 
0x1407f916f mov eax, 0x10
0x1407f9174 lea rbx, [rip + 0x15c7385]
0x1407f917b mov ecx, 1
0x1407f9180 call 0x140022a00
0x1407f9185 mov qword ptr [rax + 8], 0x18
0x1407f918d lea rdx, [rip + 0x2129d9]
0x1407f9194 mov qword ptr [rax], rdx
0x1407f9197 mov rbx, rax
0x1407f919a lea rax, [rip + 0x1687767]
0x1407f91a1 xor ecx, ecx
0x1407f91a3 mov rdi, rax
0x1407f91a6 mov rsi, rbx
0x1407f91a9 xor eax, eax
0x1407f91ab mov ebx, ecx
0x1407f91ad add rsp, 0xb8
0x1407f91b4 pop rbp
0x1407f91b5 ret 
0x1407f91b6 call 0x14024b360
0x1407f91bb nop dword ptr [rax + rax]
0x1407f91c0 test rcx, rcx
0x1407f91c3 jne 0x1407f92ef
0x1407f91c9 call 0x140112120
0x1407f91ce test rcx, rcx
0x1407f91d1 jne 0x1407f92da
0x1407f91d7 mov rsi, qword ptr [rsp + 0xd0]
0x1407f91df lea rdx, [rsi + 0x52]
0x1407f91e3 mov r9, qword ptr [rsp + 0xe0]
0x1407f91eb lea r10, [r9 - 0x52]
0x1407f91ef lea r11, [r9 - 0x5e]
0x1407f91f3 lea r12, [rsi + 0x5e]
0x1407f91f7 mov r13, qword ptr [rsp + 0xd8]
0x1407f91ff add r13, -0x5e
0x1407f9203 mov r15, qword ptr [rax + 0x20]
0x1407f9207 mov qword ptr [rsp], r12
0x1407f920b mov qword ptr [rsp + 8], r13
0x1407f9210 mov qword ptr [rsp + 0x10], r11
0x1407f9215 movups xmmword ptr [rsp + 0x18], xmm15
0x1407f921b mov qword ptr [rsp + 0x28], 0
0x1407f9224 mov rax, rbx
0x1407f9227 xor ebx, ebx
0x1407f9229 xor ecx, ecx
0x1407f922b mov edi, ecx
0x1407f922d mov rsi, rdx
0x1407f9230 mov r8d, 0xc
0x1407f9236 mov r9, r10
0x1407f9239 call r15
0x1407f923c nop dword ptr [rax]
0x1407f9240 test rdi, rdi
0x1407f9243 je 0x1407f92cd
0x1407f9249 movups xmmword ptr [rsp + 0x68], xmm15
0x1407f924f je 0x1407f9255
0x1407f9251 mov rdi, qword ptr [rdi + 8]
0x1407f9255 mov qword ptr [rsp + 0x68], rdi
0x1407f925a mov qword ptr [rsp + 0x70], rsi
0x1407f925f lea rax, [rip + 0x22692c]
0x1407f9266 mov ebx, 0x22
0x1407f926b lea rcx, [rsp + 0x68]
0x1407f9270 mov edi, 1
0x1407f9275 mov esi, edi
0x1407f9277 call 0x14013d080
0x1407f927c nop dword ptr [rax]
0x1407f9280 test rax, rax
0x1407f9283 jne 0x1407f92b8
0x1407f9285 nop 
0x1407f9286 mov eax, 0x10
0x1407f928b lea rbx, [rip + 0x15c726e]
0x1407f9292 mov ecx, 1
0x1407f9297 call 0x140022a00
0x1407f929c mov qword ptr [rax + 8], 0x22
0x1407f92a4 lea rdx, [rip + 0x2268e7]
0x1407f92ab mov qword ptr [rax], rdx
0x1407f92ae mov rbx, rax
0x1407f92b1 lea rax, [rip + 0x1687650]
0x1407f92b8 xor ecx, ecx
0x1407f92ba mov rdi, rax
0x1407f92bd mov rsi, rbx
0x1407f92c0 xor eax, eax
0x1407f92c2 mov ebx, ecx
0x1407f92c4 add rsp, 0xb8
0x1407f92cb pop rbp
0x1407f92cc ret 
0x1407f92cd xor edi, edi
0x1407f92cf xor esi, esi
0x1407f92d1 add rsp, 0xb8
0x1407f92d8 pop rbp
0x1407f92d9 ret 
0x1407f92da xor eax, eax
0x1407f92dc xor ebx, ebx
0x1407f92de mov rsi, rdi
0x1407f92e1 mov rdi, rcx
0x1407f92e4 mov ecx, ebx
0x1407f92e6 add rsp, 0xb8
0x1407f92ed pop rbp
0x1407f92ee ret 
0x1407f92ef xor eax, eax
0x1407f92f1 xor ebx, ebx
0x1407f92f3 mov rsi, rdi
0x1407f92f6 mov rdi, rcx
0x1407f92f9 mov ecx, ebx
0x1407f92fb add rsp, 0xb8
0x1407f9302 pop rbp
0x1407f9303 ret 
0x1407f9304 lea rax, [rip + 0x214452]
0x1407f930b mov ebx, 0x19
0x1407f9310 xor ecx, ecx
0x1407f9312 xor edi, edi
0x1407f9314 mov esi, edi
0x1407f9316 call 0x14013d080
0x1407f931b nop dword ptr [rax + rax]
0x1407f9320 test rax, rax
0x1407f9323 jne 0x1407f9358
0x1407f9325 nop 
0x1407f9326 mov eax, 0x10
0x1407f932b lea rbx, [rip + 0x15c71ce]
0x1407f9332 mov ecx, 1
0x1407f9337 call 0x140022a00
0x1407f933c mov qword ptr [rax + 8], 0x19
0x1407f9344 lea rdx, [rip + 0x214412]
0x1407f934b mov qword ptr [rax], rdx
0x1407f934e mov rbx, rax
0x1407f9351 lea rax, [rip + 0x16875b0]
0x1407f9358 xor ecx, ecx
0x1407f935a mov rdi, rax
0x1407f935d mov rsi, rbx
0x1407f9360 xor eax, eax
0x1407f9362 mov ebx, ecx
0x1407f9364 add rsp, 0xb8
0x1407f936b pop rbp
0x1407f936c ret 
0x1407f936d mov qword ptr [rsp + 8], rax
0x1407f9372 mov qword ptr [rsp + 0x10], rbx
0x1407f9377 mov qword ptr [rsp + 0x18], rcx
0x1407f937c mov qword ptr [rsp + 0x20], rdi
0x1407f9381 call 0x14008c6e0
0x1407f9386 mov rax, qword ptr [rsp + 8]
0x1407f938b mov rbx, qword ptr [rsp + 0x10]
0x1407f9390 mov rcx, qword ptr [rsp + 0x18]
0x1407f9395 mov rdi, qword ptr [rsp + 0x20]
0x1407f939a jmp 0x1407f8e80