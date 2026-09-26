0x14085fca0 cmp rsp, qword ptr [r14 + 0x10]
0x14085fca4 jbe 0x14085fe6f
0x14085fcaa push rbp
0x14085fcab mov rbp, rsp
0x14085fcae sub rsp, 0x78
0x14085fcb2 mov qword ptr [rsp + 0xa0], rbx
0x14085fcba mov qword ptr [rsp + 0xd8], r11
0x14085fcc2 mov qword ptr [rsp + 0xa8], rcx
0x14085fcca mov qword ptr [rsp + 0xb0], rdi
0x14085fcd2 mov qword ptr [rsp + 0xc8], r9
0x14085fcda mov qword ptr [rsp + 0xb8], rsi
0x14085fce2 mov qword ptr [rsp + 0xc0], r8
0x14085fcea mov qword ptr [rsp + 0xd0], r10
0x14085fcf2 mov rdx, qword ptr [rax]
0x14085fcf5 lea r8, [rip - 0x93c]
0x14085fcfc mov qword ptr [rsp + 0x60], r8
0x14085fd01 mov qword ptr [rsp + 0x68], rbx
0x14085fd06 mov qword ptr [rsp + 0x70], rdx
0x14085fd0b lea rax, [rip + 0xc266be]
0x14085fd12 lea rbx, [rsp + 0x60]
0x14085fd17 call 0x140286220
0x14085fd1c nop dword ptr [rax]
0x14085fd20 test rax, rax
0x14085fd23 jne 0x14085fd4d
0x14085fd25 mov rdx, qword ptr [rsp + 0xa0]
0x14085fd2d mov rax, qword ptr [rdx]
0x14085fd30 call rax
0x14085fd32 test rax, rax
0x14085fd35 je 0x14085fd60
0x14085fd37 mov rcx, qword ptr [rax + 8]
0x14085fd3b mov rdx, qword ptr [rip + 0x173af1e]
0x14085fd42 mov rsi, qword ptr [rdx]
0x14085fd45 mov edi, dword ptr [rax + 0x10]
0x14085fd48 jmp 0x14085fe27
0x14085fd4d xor ecx, ecx
0x14085fd4f mov rdi, rax
0x14085fd52 mov rsi, rbx
0x14085fd55 xor eax, eax
0x14085fd57 mov ebx, ecx
0x14085fd59 add rsp, 0x78
0x14085fd5d pop rbp
0x14085fd5e ret 
0x14085fd5f nop 
0x14085fd60 call 0x1401dad00
0x14085fd65 mov rcx, qword ptr [rax + 0x28]
0x14085fd69 mov rax, rbx
0x14085fd6c call rcx
0x14085fd6e mov rcx, rax
0x14085fd71 shl rax, 8
0x14085fd75 sub rax, rcx
0x14085fd78 mov r11, qword ptr [rsp + 0xd8]
0x14085fd80 cmp r11, rax
0x14085fd83 jle 0x14085fdc3
0x14085fd85 mov eax, 0x10
0x14085fd8a lea rbx, [rip + 0x156076f]
0x14085fd91 mov ecx, 1
0x14085fd96 call 0x140022a00
0x14085fd9b mov qword ptr [rax + 8], 0x24
0x14085fda3 lea rdx, [rip + 0x1c3846]
0x14085fdaa mov qword ptr [rax], rdx
0x14085fdad xor ebx, ebx
0x14085fdaf mov ecx, ebx
0x14085fdb1 lea rdi, [rip + 0x1620b50]
0x14085fdb8 mov rsi, rax
0x14085fdbb xor eax, eax
0x14085fdbd add rsp, 0x78
0x14085fdc1 pop rbp
0x14085fdc2 ret 
0x14085fdc3 mov rdx, qword ptr [rsp + 0x88]
0x14085fdcb mov qword ptr [rsp], rdx
0x14085fdcf mov rdx, qword ptr [rsp + 0x90]
0x14085fdd7 mov qword ptr [rsp + 8], rdx
0x14085fddc lea rax, [rip + 0xc2664d]
0x14085fde3 lea rbx, [rsp + 0x60]
0x14085fde8 mov rcx, qword ptr [rsp + 0xa8]
0x14085fdf0 mov rdi, qword ptr [rsp + 0xb0]
0x14085fdf8 mov rsi, qword ptr [rsp + 0xb8]
0x14085fe00 mov r8, qword ptr [rsp + 0xc0]
0x14085fe08 mov r9, qword ptr [rsp + 0xc8]
0x14085fe10 mov r10, qword ptr [rsp + 0xd0]
0x14085fe18 call 0x1409e8e20
0x14085fe1d xor edi, edi
0x14085fe1f xor esi, esi
0x14085fe21 add rsp, 0x78
0x14085fe25 pop rbp
0x14085fe26 ret 
0x14085fe27 mov r8, rdi
0x14085fe2a and rdi, rsi
0x14085fe2d shl rdi, 4
0x14085fe31 mov r9, qword ptr [rdi + rdx + 8]
0x14085fe36 cmp r9, rcx
0x14085fe39 je 0x14085fe65
0x14085fe3b lea rdi, [r8 + 1]
0x14085fe3f nop 
0x14085fe40 test r9, r9
0x14085fe43 jne 0x14085fe27
0x14085fe45 mov qword ptr [rsp + 0x58], rbx
0x14085fe4a lea rax, [rip + 0x173ae0f]
0x14085fe51 mov rbx, rcx
0x14085fe54 call 0x14001e7a0
0x14085fe59 mov rbx, qword ptr [rsp + 0x58]
0x14085fe5e nop 
0x14085fe60 jmp 0x14085fd60
0x14085fe65 mov rax, qword ptr [rdi + rdx + 0x10]
0x14085fe6a jmp 0x14085fd60
0x14085fe6f mov qword ptr [rsp + 0x18], rax
0x14085fe74 mov qword ptr [rsp + 0x20], rbx
0x14085fe79 mov qword ptr [rsp + 0x28], rcx
0x14085fe7e mov qword ptr [rsp + 0x30], rdi
0x14085fe83 mov qword ptr [rsp + 0x38], rsi
0x14085fe88 mov qword ptr [rsp + 0x40], r8
0x14085fe8d mov qword ptr [rsp + 0x48], r9
0x14085fe92 mov qword ptr [rsp + 0x50], r10
0x14085fe97 mov qword ptr [rsp + 0x58], r11
0x14085fe9c nop dword ptr [rax]
0x14085fea0 call 0x14008c6e0
0x14085fea5 mov rax, qword ptr [rsp + 0x18]
0x14085feaa mov rbx, qword ptr [rsp + 0x20]
0x14085feaf mov rcx, qword ptr [rsp + 0x28]
0x14085feb4 mov rdi, qword ptr [rsp + 0x30]
0x14085feb9 mov rsi, qword ptr [rsp + 0x38]
0x14085febe mov r8, qword ptr [rsp + 0x40]
0x14085fec3 mov r9, qword ptr [rsp + 0x48]
0x14085fec8 mov r10, qword ptr [rsp + 0x50]
0x14085fecd mov r11, qword ptr [rsp + 0x58]
0x14085fed2 jmp 0x14085fca0